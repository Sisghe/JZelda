package jzelda.levels;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import jzelda.entities.Direction;
import jzelda.model.GameConfig;
import jzelda.resources.ResourceManager;

/**
 * Coordinates level progression for the campaign.
 */
public class LevelManager {
    private final LevelFactory levelFactory;
    private final ResourceManager resources;
    private static final int CAMPAIGN_LEVELS = 1;
    private static final String LEVEL_PREFIX = "level";
    private static final int SINGLE_LEVEL_NUMBER = 1;
    private static final String SINGLE_LEVEL_ID = LEVEL_PREFIX + SINGLE_LEVEL_NUMBER;
    private static final int REQUIRED_GRID_ROWS = 3;
    private static final int REQUIRED_GRID_COLUMNS = 4;
    private List<Level> levels = new ArrayList<>();
    private Map<String, Integer> levelIndexById = new HashMap<>();
    private int currentIndex;
    private final Map<Integer, DungeonLayout> dungeonLayouts = new HashMap<>();
    private final Map<String, Level> roomCache = new HashMap<>();
    private final DungeonProgress dungeonProgress = new DungeonProgress();
    private DungeonLayout currentDungeonLayout;
    private String currentRoomId;

    /**
     * Creates a manager using the supplied factory.
     *
     * @param levelFactory factory used to load maps
     */
    public LevelManager(LevelFactory levelFactory) {
        this.levelFactory = levelFactory;
        this.resources = levelFactory.getResources();
        reloadCampaign();
    }

    /**
     * Reloads campaign levels. In this project only level1 is supported.
     */
    public final void reloadCampaign() {
        roomCache.clear();
        levels = new ArrayList<>(Collections.nCopies(CAMPAIGN_LEVELS, (Level) null));
        for (int i = 0; i < CAMPAIGN_LEVELS; i++) {
            int levelId = i + 1;
            levels.set(i, applyDungeonProgress(levelFactory.createLevel(levelId)));
        }
        refreshIndexById();
        currentIndex = 0;
        dungeonLayouts.clear();
        for (int i = 0; i < CAMPAIGN_LEVELS; i++) {
            DungeonLayout layout = loadDungeonLayoutIfAny(i + 1);
            if (layout != null) {
                dungeonLayouts.put(i, layout);
            }
        }
        currentDungeonLayout = dungeonLayouts.get(0);
        if (currentDungeonLayout != null) {
            currentDungeonLayout.reset();
            currentRoomId = currentDungeonLayout.getCurrentRoomId();
            dungeonProgress.visitRoom(currentRoomId);
        } else {
            currentRoomId = null;
        }
        validateDungeonConfigurations();
    }

    private void validateDungeonConfigurations() {
        for (Map.Entry<Integer, DungeonLayout> entry : dungeonLayouts.entrySet()) {
            int levelIndex = entry.getKey();
            DungeonLayout layout = entry.getValue();
            if (layout == null) {
                continue;
            }
            int levelNumber = levelIndex + 1;
            try {
                validateDungeonLayout(levelNumber, layout);
                System.out.println("VALIDATION PASSED: level" + levelNumber);
            } catch (RuntimeException ex) {
                System.out.println("VALIDATION FAILED: level" + levelNumber + " -> " + ex.getMessage());
                throw ex;
            }
        }
    }

    private Level createUnavailableCampaignPlaceholder(int levelId) {
        TileType[][] tiles = new TileType[GameConfig.MAP_ROWS][GameConfig.MAP_COLUMNS];
        for (TileType[] row : tiles) {
            Arrays.fill(row, TileType.FLOOR);
        }
        String levelName = "Livello " + levelId + " - non disponibile";
        Rectangle2D.Double defaultExit = new Rectangle2D.Double(GameConfig.toPlayX(GameConfig.MAP_COLUMNS - 2),
                GameConfig.toPlayY(GameConfig.MAP_ROWS - 2), 24, 24);
        return new Level(levelId, levelName, tiles, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                resources.loadLevelBackground(levelId), GameConfig.toPlayX(1), GameConfig.toPlayY(1), Collections.emptyList(),
                defaultExit, false);
    }

    private void validateDungeonLayout(int levelNumber, DungeonLayout layout) {
        if (layout.getRows() != REQUIRED_GRID_ROWS || layout.getColumns() != REQUIRED_GRID_COLUMNS) {
            throw new IllegalStateException("griglia non valida: " + layout.getRows() + "x" + layout.getColumns()
                    + ", attesa " + REQUIRED_GRID_ROWS + "x" + REQUIRED_GRID_COLUMNS);
        }
        if (!layout.hasRoom(layout.getStartRow(), layout.getStartColumn())) {
            throw new IllegalStateException(
                    "start cell non valida: (" + layout.getStartRow() + "," + layout.getStartColumn() + ")");
        }

        int declaredRooms = 0;
        int nullCells = 0;
        for (int row = 0; row < layout.getRows(); row++) {
            for (int col = 0; col < layout.getColumns(); col++) {
                String roomId = layout.getRoomId(row, col).orElse(null);
                if (roomId == null) {
                    nullCells++;
                    continue;
                }
                declaredRooms++;
                validateRoomTopologyAndExits(levelNumber, layout, roomId, row, col);
            }
        }
        if (declaredRooms != 9) {
            throw new IllegalStateException("numero room dichiarate non corretto: attese 9, trovate " + declaredRooms);
        }
        if (nullCells != 3) {
            throw new IllegalStateException("numero celle NULL non corretto: attese 3, trovate " + nullCells);
        }

        validateReciprocalRoomExits(levelNumber, layout);
    }

    private void validateRoomTopologyAndExits(int levelNumber, DungeonLayout layout, String roomId, int sourceRow, int sourceCol) {
        List<String> lines = resources.loadMapLinesForRoom(roomId);
        if (lines.size() != GameConfig.MAP_ROWS) {
            throw new IllegalStateException("room " + roomId + " non valida: righe " + lines.size() + " (attese " + GameConfig.MAP_ROWS + ")");
        }
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).length() != GameConfig.MAP_COLUMNS) {
                throw new IllegalStateException("room " + roomId + " non valida: riga " + (i + 1) + " length "
                        + lines.get(i).length() + ", attesa " + GameConfig.MAP_COLUMNS);
            }
        }

        List<String> mapExits = collectExitCoordinates(lines);
        Properties roomProperties = resources.loadLevelPropertiesForRoom(roomId);
        Map<String, Map<String, String>> grouped = groupExitProperties(roomProperties);
        int logicalExitCount = countLogicalExitGroups(mapExits);

        if (logicalExitCount != grouped.size()) {
            throw new IllegalStateException("room " + roomId + ": mismatch X/properties -> mapExits=" + mapExits.size()
                    + " (logical exits=" + logicalExitCount + "), properties=" + grouped.size());
        }
        for (String location : mapExits) {
            Map<String, String> values = grouped.get(location);
            if (values == null || values.isEmpty()) {
                if (grouped.containsKey(chooseRepresentativeKeyForLocation(location, mapExits))) {
                    continue;
                }
                throw new IllegalStateException("room " + roomId + ": nessuna proprietà per la porta " + location);
            }

            String id = getRequired(values, location, roomId, "id");
            String directionRaw = getRequired(values, location, roomId, "direction");
            Direction direction = parseDirectionFromRaw(roomId, directionRaw);
            String typeRaw = getOrDefault(values, "type", "ROOM");
            ExitType type = parseExitType(typeRaw, roomId);
            char marker = markerAt(lines, location);
            if (marker == 'D') {
                if (!Boolean.parseBoolean(getOrDefault(values, "requiresKey", "false"))) {
                    throw new IllegalStateException("[ERROR] D marker " + roomId + "@" + location + " richiede requiresKey=true");
                }
                if (getOrDefault(values, "doorStateId", null) == null) {
                    throw new IllegalStateException("[ERROR] D marker missing doorStateId: " + roomId + "@" + location);
                }
                if (getOrDefault(values, "visualId", null) == null) {
                    throw new IllegalStateException("[ERROR] D marker missing visualId: " + roomId + "@" + location);
                }
            }
            String target = getOrDefault(values, "target", null);
            String targetExit = getOrDefault(values, "targetExit", null);

            if (!"ROOM".equals(typeRaw.toUpperCase(Locale.ROOT)) && target != null) {
                System.out.println("[WARN] room " + roomId + " exit " + id + " usa target con tipo " + typeRaw);
            }

            if (type == ExitType.SHOP && (target == null || targetExit == null)) {
                throw new IllegalStateException("room " + roomId + " exit " + id + " (SHOP) senza target/targetExit");
            }
            if (type == ExitType.ROOM && targetExit != null && !targetExit.isBlank() && !target.isBlank()) {
                if (!resources.loadMapLinesForRoom(target).isEmpty()) {
                    // target exists, explicit resolution check is deferred to transition logic.
                }
            }

            if (type == ExitType.ROOM) {
                Optional<String> implied = layout.getAdjacentRoom(sourceRow, sourceCol, direction);
                if (target == null || target.isBlank()) {
                    if (implied.isEmpty()) {
                        throw new IllegalStateException(
                                "room " + roomId + " exit " + id + " in direzione " + direction + " non ha stanza adiacente");
                    }
                } else {
                    if (layout.findRoom(target).isEmpty()) {
                        throw new IllegalStateException("room " + roomId + " exit " + id + " points to target inesistente: " + target);
                    }
                }
            }

            if (type == ExitType.ROOM && targetExit != null && !targetExit.isBlank() && (target == null || target.isBlank())) {
                throw new IllegalStateException("room " + roomId + " exit " + id + " specifica targetExit ma manca target.");
            }

            System.out.println("[OK] " + roomId + " " + id + " -> " + sourceRow + "," + sourceCol + " " + direction + " location "
                    + location);
        }
    }

    private void validateReciprocalRoomExits(int levelNumber, DungeonLayout layout) {
        Map<String, Level> loadedLevels = new HashMap<>();
        for (String roomId : layout.declaredRooms()) {
            loadedLevels.put(roomId, levelFactory.createLevel(roomId));
        }
        for (Map.Entry<String, Level> sourceEntry : loadedLevels.entrySet()) {
            String sourceRoomId = sourceEntry.getKey();
            Level sourceLevel = sourceEntry.getValue();
            for (LevelExit sourceExit : sourceLevel.getExits()) {
                if (sourceExit.getType() != ExitType.ROOM) {
                    continue;
                }
                String targetRoomId = resolveTargetRoomId(layout, sourceExit, sourceRoomId);
                if (targetRoomId == null || targetRoomId.isBlank()) {
                    throw new IllegalStateException("[ERROR] " + sourceRoomId + " " + sourceExit.getDirection()
                            + " non ha target valido nel dungeon");
                }
                Level targetLevel = loadedLevels.get(targetRoomId);
                if (targetLevel == null) {
                    throw new IllegalStateException("target level mancante: " + sourceRoomId + " -> " + targetRoomId);
                }
                Direction opposite = sourceExit.getDirection().opposite();
                LevelExit reciprocalExit = targetLevel.getExits().stream().filter(targetExit -> {
                    if (targetExit.getType() != ExitType.ROOM || targetExit.getDirection() != opposite) {
                        return false;
                    }
                    String resolved = resolveTargetRoomId(layout, targetExit, targetRoomId);
                    return sourceRoomId.equals(resolved);
                }).findFirst().orElse(null);
                if (reciprocalExit == null) {
                    throw new IllegalStateException("[ERROR] " + sourceRoomId + " " + sourceExit.getDirection()
                            + " NON reciproco verso " + targetRoomId + " in direzione opposta " + opposite);
                }
                if (sourceExit.isInitiallyLocked() != reciprocalExit.isInitiallyLocked()) {
                    throw new IllegalStateException("[ERROR] Locked door marker mismatch between reciprocal exits: "
                            + sourceRoomId + " <-> " + targetRoomId);
                }
                if (sourceExit.isInitiallyLocked()
                        && !sourceExit.getDoorStateId().equals(reciprocalExit.getDoorStateId())) {
                    throw new IllegalStateException("[ERROR] Reciprocal D exits use different doorStateId: " + sourceRoomId
                            + " <-> " + targetRoomId);
                }
                System.out.println("[OK] " + sourceRoomId + " " + sourceExit.getDirection() + " -> " + targetRoomId + " ("
                        + opposite + ")");
            }
        }
    }

    private String resolveTargetRoomId(DungeonLayout layout, LevelExit exit, String sourceRoomId) {
        if (exit.getType() != ExitType.ROOM) {
            return null;
        }
        if (exit.getTargetLevelId() != null && !exit.getTargetLevelId().isBlank()) {
            return exit.getTargetLevelId();
        }
        RoomPosition sourcePosition = layout.findRoom(sourceRoomId).orElse(null);
        if (sourcePosition == null) {
            return null;
        }
        Optional<String> implied = layout.getAdjacentRoom(sourcePosition.row(), sourcePosition.column(), exit.getDirection());
        return implied.orElse(null);
    }

    private List<String> collectExitCoordinates(List<String> lines) {
        List<String> exits = new ArrayList<>();
        for (int row = 0; row < lines.size(); row++) {
            String line = lines.get(row);
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) == 'X' || line.charAt(col) == 'D') {
                    exits.add(row + "." + col);
                }
            }
        }
        return exits;
    }

    private int countLogicalExitGroups(List<String> mapExits) {
        if (mapExits.isEmpty()) {
            return 0;
        }
        List<List<String>> groups = new ArrayList<>();
        List<String> remaining = new ArrayList<>(mapExits);
        while (!remaining.isEmpty()) {
            String anchor = remaining.remove(0);
            List<String> group = new ArrayList<>();
            group.add(anchor);
            int index = 0;
            while (index < remaining.size()) {
                String candidate = remaining.get(index);
                if (adjacentCoordinates(anchor, candidate) || sameExitGroup(group, candidate)) {
                    group.add(candidate);
                    remaining.remove(index);
                    index = 0;
                    continue;
                }
                index++;
            }
            groups.add(group);
        }
        return groups.size();
    }

    private boolean sameExitGroup(List<String> group, String candidate) {
        for (String location : group) {
            if (adjacentCoordinates(location, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean adjacentCoordinates(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        if (leftParts.length != 2 || rightParts.length != 2) {
            return false;
        }
        int leftRow = Integer.parseInt(leftParts[0]);
        int leftCol = Integer.parseInt(leftParts[1]);
        int rightRow = Integer.parseInt(rightParts[0]);
        int rightCol = Integer.parseInt(rightParts[1]);
        return (leftRow == rightRow && Math.abs(leftCol - rightCol) == 1)
                || (leftCol == rightCol && Math.abs(leftRow - rightRow) == 1);
    }

    private char markerAt(List<String> lines, String location) {
        String[] parts = location.split("\\.");
        return lines.get(Integer.parseInt(parts[0])).charAt(Integer.parseInt(parts[1]));
    }

    private String chooseRepresentativeKeyForLocation(String location, List<String> mapExits) {
        String[] parts = location.split("\\.");
        if (parts.length != 2) {
            return location;
        }
        int row = Integer.parseInt(parts[0]);
        int col = Integer.parseInt(parts[1]);
        for (String candidate : mapExits) {
            if (candidate.equals(location)) {
                continue;
            }
            String[] candidateParts = candidate.split("\\.");
            if (candidateParts.length != 2) {
                continue;
            }
            int candidateRow = Integer.parseInt(candidateParts[0]);
            int candidateCol = Integer.parseInt(candidateParts[1]);
            if ((candidateRow == row && Math.abs(candidateCol - col) == 1)
                    || (candidateCol == col && Math.abs(candidateRow - row) == 1)) {
                return candidate;
            }
        }
        return location;
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
                throw new IllegalStateException("chiave porta non valida: " + rawKey);
            }
            int row = parseInt(parts[0], rawKey);
            int col = parseInt(parts[1], rawKey);
            grouped.computeIfAbsent(row + "." + col, key -> new HashMap<>()).put(parts[2], properties.getProperty(rawKey).trim());
        }
        return grouped;
    }

    private String getRequired(Map<String, String> values, String location, String roomId, String property) {
        String value = getOrDefault(values, property, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("porta " + roomId + "@" + location + " manca proprietà: " + property);
        }
        return value;
    }

    private String getOrDefault(Map<String, String> values, String property, String defaultValue) {
        String value = values.get(property);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private Direction parseDirectionFromRaw(String roomId, String rawDirection) {
        try {
            return Direction.valueOf(rawDirection.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("room " + roomId + " direction non valida: " + rawDirection);
        }
    }

    private ExitType parseExitType(String raw, String roomId) {
        try {
            return ExitType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("room " + roomId + " exit type non valido: " + raw);
        }
    }

    /**
     * Reloads only the current level after a continue or retry.
     */
    public void reloadCurrentLevel() {
        int currentId = getCurrentLevel().getId();
        levels.set(currentIndex, getOrCreateCanonicalLevel(currentId - 1));
        refreshIndexById();
    }

    /**
     * Changes current level by canonical id (for example {@code level1}).
     *
     * @param levelId canonical level id
     * @return {@code true} when navigation succeeded
     */
    public boolean setCurrentLevel(String levelId) {
        int previousIndex = currentIndex;
        DungeonLayout previousDungeonLayout = currentDungeonLayout;
        String previousRoomId = currentRoomId;

        String requestedLevel = extractLevelPrefix(levelId);
        if (requestedLevel != null && !SINGLE_LEVEL_ID.equals(requestedLevel)) {
            return false;
        }
        Integer target = levelIndexById.get(normalizeLevelId(levelId));
        if (target == null) {
            currentIndex = previousIndex;
            currentDungeonLayout = previousDungeonLayout;
            currentRoomId = previousRoomId;
            return false;
        }

        String currentDungeonLevel = currentDungeonLayout != null ? currentDungeonLayout.getDungeonId() : null;
        if (currentDungeonLayout != null && requestedLevel != null && currentDungeonLevel != null
                && !requestedLevel.equals(currentDungeonLevel)) {
            // Avoid implicit transitions verso un altro livello quando si sta gestendo un dungeon attivo.
            currentIndex = previousIndex;
            currentDungeonLayout = previousDungeonLayout;
            currentRoomId = previousRoomId;
            return false;
        }
        if (currentDungeonLayout != null && isRoomIdentifier(levelId)
                && !currentDungeonLayout.findRoom(levelId.trim().toLowerCase(Locale.ROOT)).isPresent()) {
            currentIndex = previousIndex;
            currentDungeonLayout = previousDungeonLayout;
            currentRoomId = previousRoomId;
            return false;
        }

        currentIndex = target;
        currentDungeonLayout = dungeonLayouts.get(currentIndex);
        if (currentDungeonLayout != null && currentDungeonLayout.getCurrentRoomId() != null) {
            currentRoomId = currentDungeonLayout.getCurrentRoomId();
            if (!isRoomIdentifier(levelId)) {
                // Con dungeon attivo, accetta solo room-id espliciti per evitare salti fuori dal dungeon corrente.
                if (isCanonicalLevelId(levelId)) {
                    currentIndex = previousIndex;
                    currentDungeonLayout = previousDungeonLayout;
                    currentRoomId = previousRoomId;
                    return false;
                }
                return true;
            }
            if (!setCurrentRoom(levelId)) {
                currentIndex = previousIndex;
                currentDungeonLayout = previousDungeonLayout;
                currentRoomId = previousRoomId;
                return false;
            }
        } else {
            Level level = getLevelById(levelId);
            if (level == null) {
                currentIndex = previousIndex;
                currentDungeonLayout = previousDungeonLayout;
                currentRoomId = previousRoomId;
                return false;
            }
            currentRoomId = level == null ? null : level.getLevelId();
        }
        return true;
    }

    private boolean isRoomIdentifier(String levelId) {
        if (levelId == null) {
            return false;
        }
        return levelId.toLowerCase().trim().matches("^level\\d+_r\\d+_c\\d+$");
    }

    private boolean isCanonicalLevelId(String levelId) {
        if (levelId == null) {
            return false;
        }
        return levelId.trim().toLowerCase().matches("^level\\d+$");
    }

    private String extractLevelPrefix(String levelId) {
        if (levelId == null) {
            return null;
        }
        String normalized = levelId.trim().toLowerCase(Locale.ROOT);
        if (isRoomIdentifier(normalized)) {
            int underline = normalized.indexOf('_');
            return underline > 0 ? normalized.substring(0, underline) : normalized;
        }
        if (isCanonicalLevelId(normalized)) {
            return normalized;
        }
        if (normalized.matches("^\\d+$")) {
            return "level" + normalized;
        }
        return null;
    }

    /**
     * Gets the current level by canonical id from cache.
     *
     * @param levelId canonical level id
     * @return matching level, if present
     */
    public Level getLevelById(String levelId) {
        if (currentDungeonLayout != null && isRoomIdentifier(levelId)) {
            String targetLevel = extractLevelPrefix(levelId);
            String currentDungeon = currentDungeonLayout.getDungeonId();
            String normalized = levelId.trim().toLowerCase(Locale.ROOT);
            if (targetLevel != null && !targetLevel.equals(currentDungeon)) {
                return null;
            }
            if (!currentDungeonLayout.findRoom(normalized).isPresent()) {
                return null;
            }
        }

        if (isRoomIdentifier(levelId)) {
            String normalizedRoomId = levelId.trim().toLowerCase(Locale.ROOT);
            Level cachedRoom = roomCache.get(normalizedRoomId);
            if (cachedRoom != null) {
                return applyDungeonProgress(cachedRoom);
            }
            try {
                Level room = applyDungeonProgress(levelFactory.createLevel(normalizedRoomId));
                roomCache.put(normalizedRoomId, room);
                return room;
            } catch (RuntimeException ex) {
                return null;
            }
        }
        Integer index = levelIndexById.get(normalizeLevelId(levelId));
        if (index == null) {
            try {
                return levelFactory.createLevel(normalizeLevelId(levelId));
            } catch (RuntimeException ex) {
                return null;
            }
        }
        return getOrCreateCanonicalLevel(index);
    }

    /**
     * @return current level
     */
    public Level getCurrentLevel() {
        if (currentDungeonLayout == null || currentRoomId == null) {
            return getOrCreateCanonicalLevel(currentIndex);
        }
        Level level = getLevelById(currentRoomId);
        if (level != null) {
            return level;
        }
        return getOrCreateCanonicalLevel(currentIndex);
    }

    /**
     * @return immutable list of loaded levels
     */
    public List<Level> getLevels() {
        return Collections.unmodifiableList(levels);
    }

    public DungeonLayout getCurrentDungeonLayout() {
        return currentDungeonLayout;
    }

    /** @return persistent opened-door state for the active dungeon run */
    public DungeonProgress getDungeonProgress() {
        return dungeonProgress;
    }

    /**
     * Synchronizes both ends of a room connection after a successful opening or
     * traversal. The operation is idempotent and also repairs already cached
     * room instances.
     */
    public void synchronizeRoomConnection(String sourceRoomId, Level sourceLevel, LevelExit sourceExit,
            String targetRoomId, Level targetLevel, LevelExit targetExit) {
        if (sourceRoomId == null || targetRoomId == null || sourceExit == null || targetExit == null
                || sourceExit.getType() != ExitType.ROOM || targetExit.getType() != ExitType.ROOM) {
            return;
        }
        String doorStateId = sourceExit.getDoorStateId();
        if (doorStateId != null && !doorStateId.trim().isEmpty()) {
            dungeonProgress.openDoor(doorStateId);
            dungeonProgress.openDoorForRooms(sourceRoomId, sourceExit.getDirection(), targetRoomId,
                    targetExit.getDirection());
        }
        applyDungeonProgress(sourceLevel);
        applyDungeonProgress(targetLevel);
        Level cachedSource = roomCache.get(sourceRoomId.trim().toLowerCase(Locale.ROOT));
        Level cachedTarget = roomCache.get(targetRoomId.trim().toLowerCase(Locale.ROOT));
        applyDungeonProgress(cachedSource);
        applyDungeonProgress(cachedTarget);
    }

    public String getCurrentRoomId() {
        return currentRoomId;
    }

    public int getCurrentRoomRow() {
        return currentDungeonLayout == null ? -1 : currentDungeonLayout.getCurrentRow();
    }

    public int getCurrentRoomColumn() {
        return currentDungeonLayout == null ? -1 : currentDungeonLayout.getCurrentColumn();
    }

    public int getCurrentRoomStartRow() {
        return currentDungeonLayout == null ? -1 : currentDungeonLayout.getStartRow();
    }

    public int getCurrentRoomStartColumn() {
        return currentDungeonLayout == null ? -1 : currentDungeonLayout.getStartColumn();
    }

    public Optional<RoomPosition> findRoomPosition(String roomId) {
        return currentDungeonLayout == null ? Optional.empty() : currentDungeonLayout.findRoom(roomId);
    }

    public Set<String> getDeclaredRooms() {
        return currentDungeonLayout == null ? Collections.emptySet() : currentDungeonLayout.declaredRooms();
    }

    public Set<String> getRoomNeighbors(String roomId) {
        if (currentDungeonLayout == null || roomId == null) {
            return Collections.emptySet();
        }
        RoomPosition position = currentDungeonLayout.findRoom(roomId).orElse(null);
        if (position == null) {
            return Collections.emptySet();
        }
        Set<String> neighbors = new HashSet<>();
        Level level = getLevelById(roomId);
        if (level == null) {
            return neighbors;
        }
        for (LevelExit exit : level.getExits()) {
            if (exit.getType() != ExitType.ROOM) {
                continue;
            }
            currentDungeonLayout.getAdjacentRoom(position.row(), position.column(), exit.getDirection())
                    .ifPresent(neighbors::add);
        }
        return neighbors;
    }

    public Optional<String> resolveAdjacentRoom(Direction direction) {
        if (currentDungeonLayout == null) {
            return Optional.empty();
        }
        return currentDungeonLayout.getAdjacentRoom(direction);
    }

    public boolean setCurrentRoom(String roomId) {
        if (currentDungeonLayout == null || roomId == null) {
            currentRoomId = null;
            return false;
        }
        String normalized = roomId.trim().toLowerCase();
        if (currentDungeonLayout.moveTo(normalized)) {
            currentRoomId = normalized;
            dungeonProgress.visitRoom(currentRoomId);
            return true;
        }
        return false;
    }

    private void refreshIndexById() {
        levelIndexById = new HashMap<>();
        for (int i = 0; i < levels.size(); i++) {
            if ((i + 1) == SINGLE_LEVEL_NUMBER) {
                levelIndexById.put(SINGLE_LEVEL_ID, i);
            }
        }
    }

    private DungeonLayout loadDungeonLayoutIfAny(int levelId) {
        Properties properties = resources.loadDungeonProperties(LEVEL_PREFIX + levelId);
        if (properties.isEmpty()) {
            return null;
        }
        int rows = parseRequiredInt(properties, "grid.rows", "level" + levelId);
        int cols = parseRequiredInt(properties, "grid.cols", "level" + levelId);
        if (rows != REQUIRED_GRID_ROWS || cols != REQUIRED_GRID_COLUMNS) {
            throw new IllegalStateException("Dungeon per level" + levelId + " richiede rows=" + REQUIRED_GRID_ROWS + ", cols="
                    + REQUIRED_GRID_COLUMNS);
        }
        int startRow = parseRequiredInt(properties, "grid.startRow", "level" + levelId);
        int startCol = parseRequiredInt(properties, "grid.startCol", "level" + levelId);
        String[][] roomIds = new String[rows][cols];
        Set<String> declared = new HashSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("cell.")) {
                continue;
            }
            String[] coord = DungeonLayout.parseCellCoordinates(key);
            int row = parseInt(coord[0], key);
            int col = parseInt(coord[1], key);
            String roomId = properties.getProperty(key).trim().toLowerCase();
            if (row < 0 || row >= rows || col < 0 || col >= cols) {
                throw new IllegalStateException("Cell coord fuori limite in level" + levelId + ": " + key);
            }
            if (roomId.isEmpty()) {
                continue;
            }
            if (!declared.add(roomId)) {
                throw new IllegalStateException("Room id duplicato in dungeon level" + levelId + ": " + roomId);
            }
            validateRoomFileExists(roomId);
            roomIds[row][col] = roomId;
        }
        DungeonLayout layout = DungeonLayout.of(LEVEL_PREFIX + levelId, roomIds, rows, cols, startRow, startCol);
        if (!layout.hasRoom(startRow, startCol)) {
            throw new IllegalStateException("Cella start non valida in level" + levelId + ": (" + startRow + "," + startCol + ")");
        }
        return layout;
    }

    private int parseRequiredInt(Properties properties, String key, String context) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Propriet�� mancante in dungeon " + context + ": " + key);
        }
        return parseInt(raw.trim(), key);
    }

    private int parseInt(String raw, String context) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Valore non valido in " + context + ": " + raw);
        }
    }

    private void validateRoomFileExists(String roomId) {
        if (resources != null) {
            resources.loadMapLinesForRoom(roomId);
        }
    }

    private String normalizeLevelId(String levelId) {
        if (levelId == null) {
            return "";
        }
        String normalized = levelId.trim().toLowerCase();
        if (normalized.matches("^\\d+$")) {
            return "level" + normalized;
        }
        if (normalized.matches("^level\\d+_r\\d+_c\\d+$")) {
            int underscore = normalized.indexOf('_');
            return underscore >= 0 ? normalized.substring(0, underscore) : normalized;
        }
        return normalized;
    }

    /**
     * @return current index, zero based
     */
    public int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * @return current level number, one based
     */
    public int getCurrentNumber() {
        return currentIndex + 1;
    }

    /**
     * Moves to the next level when available.
     *
     * @return {@code true} if there is a next level
     */
    public boolean nextLevel() {
        if (currentIndex + 1 < levels.size()) {
            int target = currentIndex + 1;
            Level next = getOrCreateCanonicalLevel(target);
            if (next == null) {
                return false;
            }
            currentIndex = target;
            levels.set(currentIndex, next);
            currentDungeonLayout = dungeonLayouts.get(currentIndex);
            if (currentDungeonLayout != null) {
                currentDungeonLayout.reset();
                currentRoomId = currentDungeonLayout.getCurrentRoomId();
            } else {
                currentRoomId = null;
            }
            return true;
        }
        return false;
    }

    /**
     * @return {@code true} when the current level is the last one
     */
    public boolean isLastLevel() {
        return currentIndex == levels.size() - 1;
    }

    /**
     * @return {@code true} when every level is marked completed
     */
    public boolean allLevelsCompleted() {
        return levels.stream().allMatch(level -> level != null && level.isCompleted());
    }

    private Level getOrCreateCanonicalLevel(int levelIndex) {
        if (levelIndex < 0 || levelIndex >= levels.size()) {
            return null;
        }
        Level level = levels.get(levelIndex);
        if (level != null) {
            return level;
        }
        return createUnavailableCampaignPlaceholder(levelIndex + 1);
    }

    private Level applyDungeonProgress(Level level) {
        if (level != null) {
            level.applyDungeonProgress(dungeonProgress);
        }
        return level;
    }
}

