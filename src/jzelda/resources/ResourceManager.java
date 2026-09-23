package jzelda.resources;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import jzelda.levels.DoorVisualState;

/**
 * Singleton resource loader for images and map files. It keeps lightweight caches
 * and returns generated placeholders when image files are missing, which keeps
 * the game runnable in educational environments.
 */
public final class ResourceManager {
    private static ResourceManager instance;
    private final Path root;
    private final Map<String, Image> spriteCache = new ConcurrentHashMap<>();
    private final Map<Integer, BufferedImage> levelBackgroundCache = new ConcurrentHashMap<>();
    private final Map<Integer, String> levelBackgroundPathCache = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> roomBackgroundCache = new ConcurrentHashMap<>();
    private final Map<String, String> roomBackgroundPathCache = new ConcurrentHashMap<>();

    private static final Pattern MAP_LEVEL_PATTERN = Pattern.compile("(?i)^level\\s*(\\d+)\\.map$");
    private static final Pattern ROOM_ID_PATTERN = Pattern.compile("(?i)^(level\\s*(\\d+)_r\\d+_c\\d+)\\.map$");
    private static final Pattern ROOM_ID_ONLY_PATTERN = Pattern.compile("(?i)^(level(\\d+)_r(\\d+)_c(\\d+))$");
    private static final Pattern ROOM_BACKGROUND_NAME_PATTERN = Pattern.compile("(?i)^level(\\d+)_r(\\d+)_c(\\d+)\\.png$");
    private static final Pattern PLAYER_FALLBACK_PATTERN = Pattern.compile("^player_(up|down|left|right)_(.+)$");
    private static final String LEVEL_BACKGROUND_BASE_DIR = "images/level 1";

    private ResourceManager(Path root) {
        this.root = root;
    }

    /**
     * Returns the singleton resource manager rooted at the current project.
     *
     * @return resource manager instance
     */
    public static synchronized ResourceManager getInstance() {
        if (instance == null) {
            instance = new ResourceManager(Paths.get("resources"));
        }
        return instance;
    }

    /**
     * Loads an image from {@code resources/images}. If no matching PNG exists, a
     * generated placeholder is returned.
     *
     * @param key logical image key without extension
     * @return loaded or generated image
     */
    public Image getSprite(String key) {
        return spriteCache.computeIfAbsent(key, this::loadSpriteOrFallback);
    }

    /** Loads a deterministic per-room door overlay, with a non-fatal fallback. */
    public Image getDoorSprite(String roomId, String visualId, DoorVisualState state, int openingFrame) {
        if (roomId == null || visualId == null || state == null) {
            return null;
        }
        String suffix;
        switch (state) {
        case CLOSED:
            suffix = "closed";
            break;
        case OPEN:
            suffix = "open";
            break;
        case OPENING:
            suffix = "opening_" + Math.max(0, Math.min(2, openingFrame));
            break;
        default:
            suffix = "closed";
            break;
        }
        String key = roomId.trim().toLowerCase(Locale.ROOT) + "__door_" + visualId.trim().toLowerCase(Locale.ROOT) + "__" + suffix;
        return spriteCache.computeIfAbsent("door/" + key, ignored -> {
            Path file = root.resolve("images").resolve("level 1").resolve("doors").resolve(key + ".png");
            Image image = loadImageOrNull(file);
            if (image == null && state == DoorVisualState.OPENING) {
                String closedKey = roomId.trim().toLowerCase(Locale.ROOT) + "__door_"
                        + visualId.trim().toLowerCase(Locale.ROOT) + "__closed";
                image = loadImageOrNull(root.resolve("images").resolve("level 1").resolve("doors").resolve(closedKey + ".png"));
            }
            if (image == null) {
                System.err.println("Mancante overlay porta: " + file);
                return createFallbackSprite(key);
            }
            return image;
        });
    }

    /**
     * Loads a level background image by map file name.
     *
     * @param mapFilename map filename (typically {@code levelN.map})
     * @return loaded background image or diagnostic placeholder
     */
    public BufferedImage loadLevelBackground(String mapFilename) {
        String normalized = mapFilename == null ? "" : mapFilename.trim().toLowerCase(Locale.ROOT);
        Matcher mapMatcher = MAP_LEVEL_PATTERN.matcher(normalized);
        if (mapMatcher.matches()) {
            int levelId = Integer.parseInt(mapMatcher.group(1));
            return levelId == 1 ? loadLevelBackground(levelId) : loadLevelBackground(1);
        }
        Matcher roomMatcher = ROOM_ID_ONLY_PATTERN.matcher(normalized.replace(".map", ""));
        if (roomMatcher.matches()) {
            int levelId = Integer.parseInt(roomMatcher.group(2));
            String roomId = roomMatcher.group(1);
            if (levelId != 1) {
                return loadLevelBackground("level1", 1);
            }
            return loadLevelBackground(roomId, levelId);
        }
        return loadLevelBackground(1);
    }

    /**
     * Loads a level background image for a specific room id.
     *
     * @param roomId logical room id (es. level1_r0_c2)
     * @param levelIdFallback numeric level id fallback
     * @return loaded background image or diagnostic fallback
     */
    public BufferedImage loadLevelBackground(String roomId, int levelIdFallback) {
        return roomBackgroundCache.computeIfAbsent(normalizeRoomBackgroundKey(roomId),
                key -> loadRoomLevelBackgroundOrFallback(key, levelIdFallback));
    }

    /** Loads an existing room background variant, or returns null when absent. */
    public BufferedImage loadRoomBackgroundVariant(String roomId, String variant) {
        if (roomId == null || variant == null || variant.trim().isEmpty()) {
            return null;
        }
        String normalizedRoomId = normalizeRoomBackgroundKey(roomId);
        String key = normalizedRoomId + "__" + variant.trim().toLowerCase(Locale.ROOT);
        return roomBackgroundCache.computeIfAbsent(key, ignored -> {
            Path file = root.resolve(LEVEL_BACKGROUND_BASE_DIR).resolve(normalizedRoomId + "_" + variant.trim().toLowerCase(Locale.ROOT) + ".png");
            if (!Files.exists(file)) {
                return null;
            }
            try {
                return ImageIO.read(file.toFile());
            } catch (IOException ex) {
                System.err.println("Impossibile caricare variante sfondo " + file + ": " + ex.getMessage());
                return null;
            }
        });
    }

    /** Loads a room image by its complete runtime sprite name. */
    public BufferedImage loadRoomSprite(String spriteName) {
        if (spriteName == null || spriteName.trim().isEmpty()) {
            return null;
        }
        String normalized = spriteName.trim().toLowerCase(Locale.ROOT);
        Path file = root.resolve(LEVEL_BACKGROUND_BASE_DIR).resolve(normalized + ".png");
        if (!Files.exists(file)) {
            System.err.println("Missing room sprite: " + file);
            return null;
        }
        try {
            return ImageIO.read(file.toFile());
        } catch (IOException ex) {
            System.err.println("Unable to load room sprite " + file + ": " + ex.getMessage());
            return null;
        }
    }

    /**
     * Loads a level background image by level identifier.
     *
     * @param levelId level number parsed from map file names
     * @return loaded background image or diagnostic placeholder
     */
    public BufferedImage loadLevelBackground(int levelId) {
        return levelBackgroundCache.computeIfAbsent(levelId, this::loadLevelBackgroundOrFallback);
    }

    /**
     * Reads a map file from {@code resources/maps}.
     *
     * @param filename map file name
     * @return map lines
     */
    public List<String> loadMapLines(String filename) {
        Path file = root.resolve("maps").resolve(filename);
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(line -> !line.startsWith(";"))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            throw new IllegalStateException("Impossibile caricare mappa " + file + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Loads a map file for a logical room id, supporting legacy and dungeon
     * layouts.
     *
     * Supported formats:
     * <ul>
     * <li>{@code levelN.map}</li>
     * <li>{@code levelN_rX_cY.map}</li>
     * <li>{@code levelN/levelN_rX_cY.map}</li>
     * </ul>
     *
     * @param roomId logical room id
     * @return map lines
     */
    public List<String> loadMapLinesForRoom(String roomId) {
        Path file = resolveMapFileForRoomId(roomId);
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(line -> !line.startsWith(";"))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            throw new IllegalStateException("Impossibile caricare mappa per room id " + roomId + " in " + file + ": "
                    + ex.getMessage(), ex);
        }
    }

    /**
     * Loads a {@code levelN.properties} file associated with a map.
     *
     * <p>
     * If no properties file is found, an empty {@link Properties} instance is
     * returned.
     * </p>
     *
     * @param levelId numeric level id
     * @return properties for the level
     */
    public Properties loadLevelProperties(int levelId) {
        Path file = root.resolve("maps").resolve("level" + levelId + ".properties");
        if (!Files.exists(file)) {
            return new Properties();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Impossibile caricare properties " + file + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Loads level properties from a map filename.
     *
     * @param mapFilename map filename (for example {@code level1.map})
     * @return loaded properties, or empty properties if not found
     */
    public Properties loadLevelProperties(String mapFilename) {
        Matcher matcher = MAP_LEVEL_PATTERN.matcher(mapFilename == null ? "" : mapFilename.trim());
        int levelId = matcher.matches() ? Integer.parseInt(matcher.group(1)) : 1;
        return loadLevelProperties(levelId);
    }

    /**
     * Loads properties for a logical room id.
     *
     * <ul>
     * <li>{@code roomId}.properties</li>
     * <li>{@code levelN/roomId.properties}</li>
     * <li>{@code levelN.properties} (legacy fallback)</li>
     * </ul>
     *
     * @param roomId logical room id
     * @return loaded properties
     */
    public Properties loadLevelPropertiesForRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return new Properties();
        }
        String normalized = roomId.trim();
        Path direct = root.resolve("maps").resolve(normalized + ".properties");
        if (Files.exists(direct)) {
            return loadPropertiesFromFile(direct);
        }
        Matcher roomMatcher = ROOM_ID_PATTERN.matcher(normalized + ".map");
        if (roomMatcher.matches()) {
            String dungeon = roomMatcher.group(1).substring(0, roomMatcher.group(1).indexOf("_"));
            Path candidate = root.resolve("maps").resolve(dungeon.toLowerCase(Locale.ROOT)).resolve(normalized + ".properties");
            if (Files.exists(candidate)) {
                return loadPropertiesFromFile(candidate);
            }
        }
        Matcher levelMatcher = MAP_LEVEL_PATTERN.matcher((normalized + ".map").toLowerCase(Locale.ROOT));
        if (levelMatcher.matches()) {
            String levelPrefix = levelMatcher.group(1);
            String startRoom = resolveStartRoomFromDungeon(levelPrefix);
            if (startRoom != null) {
                Path startRoomProperties = root.resolve("maps").resolve(startRoom + ".properties");
                if (Files.exists(startRoomProperties)) {
                    return loadPropertiesFromFile(startRoomProperties);
                }
                Path nestedStartRoomProperties = root.resolve("maps").resolve("level" + levelPrefix).resolve(startRoom + ".properties");
                if (Files.exists(nestedStartRoomProperties)) {
                    return loadPropertiesFromFile(nestedStartRoomProperties);
                }
            }
        }
        return loadLevelProperties(resolveLevelIdFromRoomId(normalized));
    }

    private int resolveLevelIdFromRoomId(String roomId) {
        Matcher matcher = MAP_LEVEL_PATTERN.matcher(roomId.toLowerCase(Locale.ROOT).trim());
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        }
        if (roomId.toLowerCase(Locale.ROOT).matches("level\\d+_r\\d+_c\\d+")) {
            String prefix = roomId.toLowerCase(Locale.ROOT).replaceFirst("(_r\\d+_c\\d+)$", "");
            Matcher l = MAP_LEVEL_PATTERN.matcher(prefix + ".map");
            if (l.matches()) {
                return Integer.parseInt(l.group(1));
            }
        }
        return 1;
    }

    /**
     * Loads dungeon topology properties (3x4) for a logical level id.
     *
     * @param levelId dungeon id like level1 or numeric level
     * @return loaded properties, or empty properties if not found
     */
    public Properties loadDungeonProperties(String levelId) {
        String normalized = normalizeDungeonId(levelId);
        Path file = root.resolve("maps").resolve(normalized).resolve("dungeon.properties");
        if (!Files.exists(file)) {
            return new Properties();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Impossibile caricare dungeon properties " + file + ": " + ex.getMessage(), ex);
        }
    }

    private String normalizeDungeonId(String levelId) {
        if (levelId == null || levelId.trim().isEmpty()) {
            return "level1";
        }
        String trimmed = levelId.trim().toLowerCase(Locale.ROOT);
        if (trimmed.matches("^\\d+$")) {
            return "level" + trimmed;
        }
        if (trimmed.startsWith("level")) {
            return trimmed;
        }
        return "level" + trimmed;
    }

    /**
     * Lists map files using a stream sort pipeline.
     *
     * @return map file names sorted alphabetically
     */
    public List<String> listMapFiles() {
        Path maps = root.resolve("maps");
        try (Stream<Path> stream = Files.list(maps)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".map"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            return List.of();
        }
    }

    /**
     * Resolves a path relative to the resources folder.
     *
     * @param first first path component
     * @param more additional path components
     * @return resolved path
     */
    public Path resolve(String first, String... more) {
        return root.resolve(Paths.get(first, more));
    }

    private Properties loadPropertiesFromFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Impossibile caricare properties " + file + ": " + ex.getMessage(), ex);
        }
    }

    private Path resolveMapFileForRoomId(String roomId) {
        Path direct = root.resolve("maps").resolve(roomId + ".map");
        if (Files.exists(direct)) {
            return direct;
        }
        String normalized = roomId == null ? "" : roomId.trim().toLowerCase(Locale.ROOT);
        Matcher levelMatcher = Pattern.compile("^level\\s*(\\d+)$").matcher(normalized);
        if (levelMatcher.matches()) {
            String levelNumber = levelMatcher.group(1);
            Path dungeonProperties = root.resolve("maps").resolve("level" + levelNumber).resolve("dungeon.properties");
            String startRoom = loadStartRoomFromDungeonProperties(dungeonProperties);
            if (startRoom != null && !startRoom.isBlank()) {
                Path start = root.resolve("maps").resolve(startRoom + ".map");
                if (Files.exists(start)) {
                    return start;
                }
                Path nested = root.resolve("maps").resolve("level" + levelNumber).resolve(startRoom + ".map");
                if (Files.exists(nested)) {
                    return nested;
                }
            }
        }
        Matcher roomMatcher = ROOM_ID_PATTERN.matcher((roomId == null ? "" : roomId).toLowerCase(Locale.ROOT) + ".map");
        if (roomMatcher.matches()) {
            int firstUnderscore = roomId.indexOf('_');
            if (firstUnderscore > 0) {
                String dungeon = roomId.substring(0, firstUnderscore);
                Path nested = root.resolve("maps").resolve(dungeon.toLowerCase(Locale.ROOT)).resolve(roomId + ".map");
                if (Files.exists(nested)) {
                    return nested;
                }
            }
        }
        throw new IllegalStateException("Impossibile risolvere mappa per room id: " + roomId);
    }

    private String loadStartRoomFromDungeonProperties(Path dungeonPropertiesPath) {
        if (!Files.exists(dungeonPropertiesPath)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(dungeonPropertiesPath, StandardCharsets.UTF_8);
            java.util.Map<String, String> props = new java.util.HashMap<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (!trimmed.contains("=")) {
                    continue;
                }
                String[] pair = trimmed.split("=", 2);
                props.put(pair[0].trim().toLowerCase(Locale.ROOT), pair[1].trim());
            }
            String startRow = props.get("grid.startrow");
            String startCol = props.get("grid.startcol");
            String startRoom = null;
            if (startRow != null && startCol != null) {
                try {
                    String key = "cell." + startRow.trim() + "." + startCol.trim();
                    startRoom = props.get(key.toLowerCase(Locale.ROOT));
                } catch (RuntimeException ex) {
                    // fallback below
                }
            }
            return startRoom;
        } catch (IOException ex) {
            return null;
        }
    }

    private String resolveStartRoomFromDungeon(String levelPrefix) {
        if (levelPrefix == null || levelPrefix.isBlank()) {
            return null;
        }
        String levelId = "level" + levelPrefix;
        Path dungeonProperties = root.resolve("maps").resolve(levelId.toLowerCase(Locale.ROOT)).resolve("dungeon.properties");
        return loadStartRoomFromDungeonProperties(dungeonProperties);
    }

    /**
     * Returns the resolved resource path used for a loaded level background.
     * Example: {@code /images/level 1/level1_r1_c1.png}
     *
     * @param levelId level identifier
     * @return resource path in classpath-like format
     */
    public String getLevelBackgroundPath(int levelId) {
        return levelBackgroundPathCache.computeIfAbsent(levelId, this::buildActiveLevelBackgroundPath);
    }

    public String getLevelBackgroundPath(String roomId, int levelIdFallback) {
        return roomBackgroundPathCache.computeIfAbsent(normalizeRoomBackgroundKey(roomId),
                key -> buildActiveRoomBackgroundPath(key, levelIdFallback));
    }

    private String normalizeRoomBackgroundKey(String roomId) {
        return roomId == null ? "" : roomId.trim().toLowerCase(Locale.ROOT);
    }

    private BufferedImage loadLevelBackgroundOrFallback(int levelId) {
        Path file = resolveAnyRoomBackgroundForLevel(levelId);
        String backgroundFile = "livello " + Math.max(levelId, 1) + " - sfondo non configurato";
        String cachePath = missingLevelBackgroundPath(levelId);

        if (file != null && Files.exists(file)) {
            try {
                BufferedImage image = ImageIO.read(file.toFile());
                levelBackgroundPathCache.put(levelId, toResourcePath(file));
                return image;
            } catch (IOException ex) {
                System.err.println("Impossibile caricare sfondo livello " + levelId + " da " + file + ": " + ex.getMessage());
            }
        } else {
            System.err.println(
                    "Nessun PNG di sfondo trovato per level " + levelId + ". Percorso tentato: " + (file == null ? "<nessun file candidate>" : file));
        }

        levelBackgroundPathCache.putIfAbsent(levelId, cachePath);
        return createFallbackLevelBackground(backgroundFile);
    }

    private String buildActiveRoomBackgroundPath(String normalizedRoomId, int levelIdFallback) {
        Path resolved = resolveRoomBackgroundPath(normalizedRoomId, levelIdFallback);
        if (Files.exists(resolved)) {
            return toResourcePath(resolved);
        }
        return missingRoomBackgroundPath(Math.max(levelIdFallback, 1), normalizedRoomId);
    }

    private BufferedImage loadRoomLevelBackgroundOrFallback(String normalizedRoomId, int levelIdFallback) {
        Path file = resolveRoomBackgroundPath(normalizedRoomId, levelIdFallback);
        int boundedLevel = Math.max(levelIdFallback, 1);
        String fallbackMessage = "room " + normalizedRoomId + " -> sfondo non configurato (livello " + boundedLevel + ")";
        String cacheKey = normalizeRoomBackgroundKey(normalizedRoomId);

        try {
            if (Files.exists(file)) {
                BufferedImage image = ImageIO.read(file.toFile());
                roomBackgroundPathCache.put(cacheKey, toResourcePath(file));
                return image;
            }
            System.err.println("Mancante sfondo stanza richiesto: " + normalizedRoomId + " -> " + file);
        } catch (IOException ex) {
            System.err.println(
                    "Impossibile caricare sfondo stanza " + normalizedRoomId + " da " + file + ": " + ex.getMessage());
        }

        String missingMarker = missingRoomBackgroundPath(boundedLevel, normalizedRoomId);
        roomBackgroundPathCache.putIfAbsent(cacheKey, missingMarker);
        return createFallbackLevelBackground(fallbackMessage);
    }

    private Path resolveRoomBackgroundPath(String roomId, int levelIdFallback) {
        String normalized = normalizeRoomBackgroundKey(roomId);
        Matcher matcher = ROOM_ID_ONLY_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalStateException("Room id non valido per sfondo: " + roomId
                    + " (atteso formato levelN_rR_cC)");
        }
        String levelId = matcher.group(2);
        String row = matcher.group(3);
        String col = matcher.group(4);
        return root.resolve(LEVEL_BACKGROUND_BASE_DIR).resolve("level" + levelId + "_r" + row + "_c" + col + ".png");
    }

    private String missingRoomBackgroundPath(int levelId, String roomId) {
        return "/missing/" + normalizeDungeonId("level" + Math.max(levelId, 1)) + "/" + roomId + ".png";
    }

    private String missingLevelBackgroundPath(int levelId) {
        return "/missing/level-" + Math.max(levelId, 1) + "-background";
    }

    private Path resolveAnyRoomBackgroundForLevel(int levelId) {
        Path imagesDir = root.resolve(LEVEL_BACKGROUND_BASE_DIR);
        if (!Files.isDirectory(imagesDir)) {
            return null;
        }
        List<Path> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.list(imagesDir)) {
            matches = stream.filter(Files::isRegularFile).filter(path -> {
                Matcher matcher = ROOM_BACKGROUND_NAME_PATTERN.matcher(path.getFileName().toString().toLowerCase(Locale.ROOT));
                if (!matcher.matches()) {
                    return false;
                }
                return Integer.parseInt(matcher.group(1)) == levelId;
            }).collect(Collectors.toList());
        } catch (IOException ex) {
            return null;
        }
        if (matches.isEmpty()) {
            return null;
        }
        matches.sort((a, b) -> {
            Matcher ma = ROOM_BACKGROUND_NAME_PATTERN.matcher(a.getFileName().toString().toLowerCase(Locale.ROOT));
            Matcher mb = ROOM_BACKGROUND_NAME_PATTERN.matcher(b.getFileName().toString().toLowerCase(Locale.ROOT));
            if (!ma.matches() || !mb.matches()) {
                return a.compareTo(b);
            }
            int rowA = Integer.parseInt(ma.group(2));
            int rowB = Integer.parseInt(mb.group(2));
            if (rowA != rowB) {
                return Integer.compare(rowA, rowB);
            }
            int colA = Integer.parseInt(ma.group(3));
            int colB = Integer.parseInt(mb.group(3));
            if (colA != colB) {
                return Integer.compare(colA, colB);
            }
            return a.getFileName().toString().compareTo(b.getFileName().toString());
        });
        return matches.get(0);
    }

    private String buildActiveLevelBackgroundPath(Integer levelId) {
        Path roomBackground = resolveAnyRoomBackgroundForLevel(levelId);
        if (roomBackground != null) {
            return toResourcePath(roomBackground);
        }
        return missingLevelBackgroundPath(levelId);
    }

    private String toResourcePath(Path absolutePath) {
        try {
            return "/" + root.relativize(absolutePath).toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return "/" + absolutePath.toString().replace('\\', '/');
        }
    }

    

    private Image loadSpriteOrFallback(String key) {
        Path file = root.resolve("images").resolve(key + ".png");
        Image image = loadImageOrNull(file);
        if (image != null) {
            return image;
        }

        Image fallback = loadPlayerDirectionalFallback(key);
        if (fallback != null) {
            return fallback;
        }

        if (key.startsWith("player_down_")) {
            System.err.println("Impossibile caricare player sprite per key " + key
                    + ". L'asset potrebbe essere in formato WebP non supportato da ImageIO. Viene usata una sostituzione.");
        }
        return createFallbackSprite(key);
    }

    private Image loadPlayerDirectionalFallback(String key) {
        Matcher matcher = PLAYER_FALLBACK_PATTERN.matcher(key);
        if (!matcher.matches()) {
            return null;
        }

        String direction = matcher.group(1);
        String animation = matcher.group(2);
        String frame = isNumericFrame(animation) ? animation : "0";
        boolean hasMovementFrame = isMovementFrame(animation);

        if (animation.startsWith("attack_") || animation.startsWith("shield_")) {
            frame = "0";
            hasMovementFrame = false;
        }

        String[] fallbackDirectionOrder = directionOrderFor(direction);
        for (String fallbackDirection : fallbackDirectionOrder) {
            Path fallback = root.resolve("images").resolve("player_" + fallbackDirection + "_" + animation + ".png");
            Image fallbackImage = loadImageOrNull(fallback);
            if (fallbackImage != null) {
                return fallbackImage;
            }

            Path fallbackZero = root.resolve("images").resolve("player_" + fallbackDirection + "_" + frame + ".png");
            fallbackImage = loadImageOrNull(fallbackZero);
            if (fallbackImage != null) {
                return fallbackImage;
            }

            if (hasMovementFrame && !"0".equals(frame)) {
                Path fallbackStatic = root.resolve("images").resolve("player_" + fallbackDirection + "_0.png");
                fallbackImage = loadImageOrNull(fallbackStatic);
                if (fallbackImage != null) {
                    return fallbackImage;
                }
            }
        }
        return null;
    }

    private boolean isNumericFrame(String animation) {
        return animation != null && animation.length() == 1 && animation.charAt(0) >= '0' && animation.charAt(0) <= '9';
    }

    private boolean isMovementFrame(String animation) {
        return "0".equals(animation) || "1".equals(animation);
    }

    private String[] directionOrderFor(String direction) {
        if ("down".equals(direction)) {
            return new String[] { "down", "up", "left", "right" };
        }
        return new String[] { direction, "down", "up", "left", "right" };
    }

    private BufferedImage createFallbackLevelBackground(String backgroundFileName) {
        BufferedImage image = new BufferedImage(jzelda.model.GameConfig.ROOM_WIDTH, jzelda.model.GameConfig.ROOM_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(20, 22, 30));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new Color(255, 80, 60));
        g.fillRect(0, 0, image.getWidth(), 8);
        g.fillRect(0, 0, 8, image.getHeight());
        g.fillRect(image.getWidth() - 8, 0, 8, image.getHeight());
        g.fillRect(0, image.getHeight() - 8, image.getWidth(), 8);
        g.setColor(new Color(255, 240, 196));
        g.drawString("MISSING LEVEL BG: " + backgroundFileName, 18, 24);
        g.drawString("Controlla risorse in resources/images/level 1", 18, 44);
        g.dispose();
        return image;
    }

    private Image loadImageOrNull(Path imagePath) {
        if (!Files.exists(imagePath)) {
            return null;
        }
        try {
            if (isWebpFormat(imagePath)) {
                return null;
            }
            return ImageIO.read(imagePath.toFile());
        } catch (IOException ex) {
            System.err.println("Impossibile caricare immagine " + imagePath + ": " + ex.getMessage());
            return null;
        }
    }

    private boolean isWebpFormat(Path imagePath) {
        try {
            byte[] magic = Files.readAllBytes(imagePath);
            return magic.length >= 12 && magic[0] == 'R' && magic[1] == 'I' && magic[2] == 'F' && magic[3] == 'F'
                    && magic[8] == 'W' && magic[9] == 'E' && magic[10] == 'B' && magic[11] == 'P';
        } catch (IOException ex) {
            return false;
        }
    }

    private Image createFallbackSprite(String key) {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        int hash = Math.abs(key.hashCode());
        g.setColor(new Color(80 + hash % 120, 60 + (hash / 3) % 120, 50 + (hash / 7) % 120));
        g.fillRect(2, 2, 20, 20);
        g.setColor(Color.BLACK);
        g.drawRect(2, 2, 20, 20);
        g.dispose();
        return image;
    }

}
