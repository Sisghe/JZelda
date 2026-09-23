package jzelda.levels;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import jzelda.entities.Direction;
import jzelda.entities.EntityFactory;
import jzelda.items.ItemFactory;
import jzelda.model.GameConfig;
import jzelda.resources.ResourceManager;

/**
 * Factory Method loader for playable levels. It parses ASCII maps stored under
 * {@code resources/maps/} and creates enemies, items and collision tiles.
 */
public class LevelFactory {
    private final ResourceManager resources;
    private final EntityFactory entityFactory = new EntityFactory();
    private final ItemFactory itemFactory = new ItemFactory();
    private static final String MAP_PREFIX = "level";
    private static final String SINGLE_LEVEL_ID = MAP_PREFIX + "1";
    private static final int SINGLE_LEVEL_NUMBER = 1;
    private static final Pattern ROOM_ID_PATTERN = Pattern.compile("(?i)^level(\\d+)_r(\\d+)_c(\\d+)$");
    private static final String LEGACY_EXIT_ID = "legacy";
    private static final int ENTITY_OFFSET = (GameConfig.TILE_SIZE - 24) / 2;
    private static final int ENTITY_DROP_OFFSET = 8;
    private static final int ITEM_DROP_OFFSET = 4;

    /**
     * Creates a level factory.
     *
     * @param resources resource manager used to read map files
     */
    public LevelFactory(ResourceManager resources) {
        this.resources = resources;
    }

    ResourceManager getResources() {
        return resources;
    }

    /**
     * Loads a level from {@code resources/maps/levelN.map}.
     *
     * @param id level number (solo level1 supportato in questo progetto)
     * @return parsed level
     */
    public Level createLevel(int id) {
        if (id != SINGLE_LEVEL_NUMBER) {
            throw new IllegalStateException("Solo " + SINGLE_LEVEL_ID + " è supportato in questo progetto: richiesto level" + id);
        }
        return createLevel(SINGLE_LEVEL_ID);
    }

    /**
     * Loads a room by logical room id (legacy or dungeon room id).
     *
     * @param roomId room identifier (e.g. {@code level1} or {@code level1_r1_c2})
     * @return parsed level
     */
    public Level createLevel(String roomId) {
        ensureLevelOneNamespace(roomId);
        int numericLevelId = parseLevelOneRoomId(roomId);
        String filename = normalizeMapFilename(roomId);
        List<String> lines = resources.loadMapLinesForRoom(filename);
        validate(lines, filename);
        TileType[][] tiles = new TileType[GameConfig.MAP_ROWS][GameConfig.MAP_COLUMNS];
        List<jzelda.entities.Enemy> enemies = new ArrayList<>();
        List<ItemDrop> itemDrops = new ArrayList<>();
        List<RupeePickup> rupees = new ArrayList<>();
        List<LevelExit> configuredExits = new ArrayList<>();
        List<ExitMarker> mapExits = new ArrayList<>();
        String normalizedFilename = normalizeMapFilename(filename);
        String normalizedRoomId = normalizedFilename == null ? "" : normalizedFilename.trim().toLowerCase(Locale.ROOT);
        java.awt.image.BufferedImage background = resolveRoomBackground(normalizedRoomId, numericLevelId);
        double startX = GameConfig.toPlayX(1);
        double startY = GameConfig.toPlayY(1);
        Rectangle2D.Double defaultExit = defaultLegacyExit();
        for (int row = 0; row < GameConfig.MAP_ROWS; row++) {
            String line = lines.get(row);
            for (int col = 0; col < GameConfig.MAP_COLUMNS; col++) {
                char c = line.charAt(col);
                int cellX = GameConfig.toPlayX(col);
                int cellY = GameConfig.toPlayY(row);
                double x = cellX + ENTITY_OFFSET;
                double y = cellY + ENTITY_OFFSET;
                tiles[row][col] = TileType.FLOOR;
                switch (c) {
                case '#':
                    tiles[row][col] = TileType.WALL;
                    break;
                case 'X':
                    tiles[row][col] = TileType.EXIT;
                    mapExits.add(
                            new ExitMarker(row, col,
                                    new Rectangle2D.Double(cellX, cellY, GameConfig.TILE_SIZE, GameConfig.TILE_SIZE), false));
                    break;
                case 'D':
                    tiles[row][col] = TileType.DOOR_LOCKED;
                    mapExits.add(
                        new ExitMarker(row, col,
                                    new Rectangle2D.Double(cellX, cellY, GameConfig.TILE_SIZE, GameConfig.TILE_SIZE), true));
                    break;
                case 'P':
                    startX = x;
                    startY = y;
                    break;
                case 'A':
                case 'W':
                    enemies.add(entityFactory.createEnemy(c, x, y));
                    break;
                case 'R':
                    rupees.add(new RupeePickup(cellX + ENTITY_DROP_OFFSET, cellY + ENTITY_DROP_OFFSET,
                            numericLevelId >= 6 ? 5 : 3));
                    break;
                case 'H':
                case 'K':
                case 'S':
                case 'L':
                case 'B':
                case 'T':
                    itemDrops.add(new ItemDrop(itemFactory.createItem(c), cellX + ITEM_DROP_OFFSET, cellY + ITEM_DROP_OFFSET));
                    break;
                default:
                    tiles[row][col] = TileType.FLOOR;
                    break;
                }
            }
        }

        configuredExits = buildConfiguredExits(filename, mapExits, roomId);
        if (configuredExits.isEmpty()) {
            if (mapExits.size() == 1) {
                configuredExits.add(createLegacyLevelExit(mapExits.get(0).bounds, numericLevelId % 2 == 0));
            } else if (mapExits.isEmpty()) {
                configuredExits.add(new LevelExit(LEGACY_EXIT_ID, defaultExit, Direction.DOWN, null, null,
                        ExitType.LEVEL_COMPLETE, numericLevelId % 2 == 0, true));
            } else {
                    throw new IllegalStateException(
                        roomId + " contiene " + mapExits.size()
                                + " porte (X/D) ma nessun properties file configurato. Definire le uscite nel properties.");
            }
        }

        return new Level(numericLevelId, "Livello " + numericLevelId + " - " + levelName(numericLevelId), tiles, enemies, itemDrops,
                rupees, background, startX, startY, configuredExits, defaultExit, numericLevelId % 2 == 0);
    }

    private int parseLevelOneRoomId(String roomId) {
        String normalized = roomId == null ? "" : roomId.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(SINGLE_LEVEL_ID)) {
            return SINGLE_LEVEL_NUMBER;
        }
        if (ROOM_ID_PATTERN.matcher(normalized).matches()) {
            return SINGLE_LEVEL_NUMBER;
        }
        if (normalized.matches("^\\d+$")) {
            int parsed = Integer.parseInt(normalized);
            if (parsed != SINGLE_LEVEL_NUMBER) {
                throw new IllegalStateException(
                        "Namespace non supportato: previsto solo 'level1' e stanze 'level1_rX_cY'. Ricevuto: " + roomId);
            }
            return SINGLE_LEVEL_NUMBER;
        }
        throw new IllegalStateException(
                "Room id non valido per il namespace level1: " + roomId
                        + " (sono supportate solo stanze che iniziano con level1)");
    }

    private void ensureLevelOneNamespace(String roomId) {
        String levelPrefix = extractLevelPrefix(roomId);
        if (!SINGLE_LEVEL_ID.equals(levelPrefix)) {
            throw new IllegalStateException(
                    "Stanze non supportate: previsto solo namespace '" + SINGLE_LEVEL_ID + "'. Input ricevuto: " + roomId);
        }
    }

    private String extractLevelPrefix(String roomId) {
        if (roomId == null) {
            return null;
        }
        String normalized = roomId.trim().toLowerCase(Locale.ROOT);
        if (ROOM_ID_PATTERN.matcher(normalized).matches()) {
            return SINGLE_LEVEL_ID;
        }
        if (normalized.matches("^level\\d+$")) {
            String levelNumber = normalized.substring("level".length());
            return "level" + levelNumber;
        }
        return normalized;
    }

    private java.awt.image.BufferedImage resolveRoomBackground(String normalizedRoomId, int numericLevelId) {
        if (ROOM_ID_PATTERN.matcher(normalizedRoomId).matches()) {
            return resources.loadLevelBackground(normalizedRoomId, numericLevelId);
        }
        return resources.loadLevelBackground(SINGLE_LEVEL_ID);
    }

    private String normalizeMapFilename(String roomId) {
        return roomId == null ? "" : roomId;
    }

    private void validate(List<String> lines, String filename) {
        if (lines.size() != GameConfig.MAP_ROWS) {
            throw new IllegalStateException(
                    "Formato mappa non valido per " + filename + ": attese " + GameConfig.MAP_ROWS + " righe, trovate "
                            + lines.size() + ". La mappa deve essere 16x11 (16 caratteri x 11 righe).");
        }
        for (String line : lines) {
            if (line.length() != GameConfig.MAP_COLUMNS) {
                throw new IllegalStateException("Formato mappa non valido per " + filename + ": ogni riga deve avere "
                        + GameConfig.MAP_COLUMNS + " caratteri, trovata riga con " + line.length() + " caratteri. "
                        + "La mappa deve essere 16x11 (16 caratteri x 11 righe).");
            }
        }
    }

    private List<LevelExit> buildConfiguredExits(String filename, List<ExitMarker> mapExits, String roomId) {
        Properties properties = resources.loadLevelPropertiesForRoom(roomId);
        if (properties.isEmpty()) {
            return List.of();
        }

        if (mapExits.isEmpty()) {
            throw new IllegalStateException("Configurazione porte trovata per " + filename
                    + " ma nel file .map non ci sono uscite contrassegnate con X.");
        }

        Map<String, Map<String, String>> grouped = groupExitProperties(properties);
        List<List<ExitMarker>> exitGroups = clusterAdjacentExitMarkers(mapExits);
        List<LevelExit> exits = new ArrayList<>();
        for (List<ExitMarker> group : exitGroups) {
            ExitMarker representative = group.get(0);
            String key = representative.row + "." + representative.col;
            Map<String, String> values = grouped.get(key);
            if (values == null || values.isEmpty()) {
                String fallbackKey = findConfiguredKeyForGroup(group, grouped);
                if (fallbackKey == null) {
                    throw new IllegalStateException("Punto X a (" + representative.row + "," + representative.col + ") in " + filename
                            + " non ha una configurazione nel properties associato (es. exit." + key + ".id).");
                }
                values = grouped.get(fallbackKey);
                key = fallbackKey;
            }

            String exitId = getRequired(values, key, filename, "id");
            String directionRaw = getRequired(values, key, filename, "direction");
            Direction direction = parseDirection(filename, directionRaw);
            ExitType type = parseExitType(filename, getOrDefault(values, "type", "ROOM"));
            String targetLevelId = getOrDefault(values, "target", null);
            String targetExitId = getOrDefault(values, "targetExit", null);
            if (type == ExitType.SHOP && (targetLevelId == null || targetExitId == null)) {
                throw new IllegalStateException("Exit " + exitId + " in " + filename + " richiede target e targetExit.");
            }
            if (type == ExitType.ROOM && !isBlank(targetLevelId) && isBlank(targetExitId)) {
                targetExitId = null;
            }
            boolean requiresKey = Boolean.parseBoolean(getOrDefault(values, "requiresKey", "false"));
            boolean requiresEnemies = Boolean.parseBoolean(getOrDefault(values, "requiresEnemies", "false"));
            boolean initiallyLocked = group.stream().anyMatch(marker -> marker.initiallyLocked);
            String doorStateId = getOrDefault(values, "doorStateId", null);
            String visualId = getOrDefault(values, "visualId", null);
            if (initiallyLocked && !requiresKey) {
                requiresKey = true;
            }
            if (initiallyLocked && isBlank(doorStateId)) {
                throw new IllegalStateException("Porta D " + key + " in " + filename + " manca doorStateId.");
            }
            if (initiallyLocked && isBlank(visualId)) {
                visualId = defaultVisualId(direction);
            }

            Rectangle2D.Double bounds = unionBounds(group);
            exits.add(new LevelExit(exitId, bounds, direction, targetLevelId, targetExitId, type, requiresKey, requiresEnemies,
                    initiallyLocked, doorStateId, visualId, representative.row, representative.col));
        }
        return exits;
    }

    private String findConfiguredKeyForGroup(List<ExitMarker> group, Map<String, Map<String, String>> grouped) {
        for (ExitMarker marker : group) {
            String key = marker.row + "." + marker.col;
            if (grouped.containsKey(key) && grouped.get(key) != null && !grouped.get(key).isEmpty()) {
                return key;
            }
        }
        return null;
    }

    private List<List<ExitMarker>> clusterAdjacentExitMarkers(List<ExitMarker> mapExits) {
        List<List<ExitMarker>> groups = new ArrayList<>();
        List<ExitMarker> remaining = new ArrayList<>(mapExits);
        while (!remaining.isEmpty()) {
            ExitMarker anchor = remaining.remove(0);
            List<ExitMarker> group = new ArrayList<>();
            group.add(anchor);
            int index = 0;
            while (index < remaining.size()) {
                ExitMarker candidate = remaining.get(index);
                if (isAdjacentToAny(group, candidate)) {
                    group.add(candidate);
                    remaining.remove(index);
                    index = 0;
                    continue;
                }
                index++;
            }
            groups.add(group);
        }
        return groups;
    }

    private boolean isAdjacentToAny(List<ExitMarker> group, ExitMarker candidate) {
        for (ExitMarker marker : group) {
            if (sameExitLine(marker, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameExitLine(ExitMarker left, ExitMarker right) {
        int rowDelta = Math.abs(left.row - right.row);
        int colDelta = Math.abs(left.col - right.col);
        return (left.row == right.row && colDelta == 1) || (left.col == right.col && rowDelta == 1);
    }

    private Rectangle2D.Double unionBounds(List<ExitMarker> markers) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (ExitMarker marker : markers) {
            Rectangle2D.Double bounds = marker.bounds;
            minX = Math.min(minX, bounds.x);
            minY = Math.min(minY, bounds.y);
            maxX = Math.max(maxX, bounds.x + bounds.width);
            maxY = Math.max(maxY, bounds.y + bounds.height);
        }
        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String defaultVisualId(Direction direction) {
        switch (direction) {
        case UP:
            return "north";
        case DOWN:
            return "south";
        case LEFT:
            return "west";
        case RIGHT:
            return "east";
        default:
            return null;
        }
    }

    private Map<String, Map<String, String>> groupExitProperties(Properties properties) {
        Map<String, Map<String, String>> grouped = new HashMap<>();
        for (String rawKey : properties.stringPropertyNames()) {
            if (!rawKey.startsWith("exit.")) {
                continue;
            }
            String tail = rawKey.substring("exit.".length());
            String[] parts = tail.split("\\.", 3);
            if (parts.length != 3) {
                throw new IllegalStateException("Proprietà porta non valida: " + rawKey
                        + " (formato atteso: exit.<row>.<col>.<campo>)");
            }
            int row = parseIntOrThrow(parts[0], rawKey, "riga");
            int col = parseIntOrThrow(parts[1], rawKey, "colonna");
            String locationKey = row + "." + col;
            String field = parts[2];
            grouped.computeIfAbsent(locationKey, ignored -> new HashMap<>()).put(field, properties.getProperty(rawKey).trim());
        }
        return grouped;
    }

    private int parseIntOrThrow(String value, String sourceKey, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Valore non valido per " + label + " in " + sourceKey + ": " + value);
        }
    }

    private String getRequired(Map<String, String> values, String key, String filename, String property) {
        String value = getOrDefault(values, property, null);
        if (value == null) {
            throw new IllegalStateException("Proprietà mancante per exit " + key + " in " + filename + ": " + property);
        }
        return value;
    }

    private String getOrDefault(Map<String, String> values, String property, String defaultValue) {
        String value = values.get(property);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private Direction parseDirection(String filename, String rawValue) {
        try {
            return Direction.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Direction non valida in " + filename + ": " + rawValue);
        }
    }

    private ExitType parseExitType(String filename, String rawValue) {
        try {
            return ExitType.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Exit type non valido in " + filename + ": " + rawValue);
        }
    }

    private LevelExit createLegacyLevelExit(Rectangle2D.Double bounds, boolean requiresKey) {
        return new LevelExit(LEGACY_EXIT_ID, bounds, Direction.DOWN, null, null, ExitType.LEVEL_COMPLETE, requiresKey, true);
    }

    private String normalizeLevelId(String rawLevelId) {
        if (rawLevelId == null || rawLevelId.trim().isEmpty()) {
            return null;
        }
        String normalized = rawLevelId.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("^\\d+$")) {
            return MAP_PREFIX + normalized;
        }
        return normalized;
    }

    private Rectangle2D.Double defaultLegacyExit() {
        return new Rectangle2D.Double(GameConfig.toPlayX(GameConfig.MAP_COLUMNS - 2), GameConfig.toPlayY(GameConfig.MAP_ROWS - 2),
                24, 24);
    }

    private static final class ExitMarker {
        private final int row;
        private final int col;
        private final Rectangle2D.Double bounds;
        private final boolean initiallyLocked;

        private ExitMarker(int row, int col, Rectangle2D.Double bounds, boolean initiallyLocked) {
            this.row = row;
            this.col = col;
            this.bounds = bounds;
            this.initiallyLocked = initiallyLocked;
        }
    }

    private String levelName(int id) {
        switch (id) {
        case 1:
            return "Bosco del Crepuscolo";
        case 2:
            return "Rovine della Fonte";
        case 3:
            return "Caverna delle Lanterne";
        case 4:
            return "Ponte dei Guardiani";
        case 5:
            return "Palude di Vetro";
        case 6:
            return "Miniere del Vento";
        case 7:
            return "Tempio Spezzato";
        case 8:
            return "Torre dell'Alba";
        default:
            return "Sconosciuto";
        }
    }
}
