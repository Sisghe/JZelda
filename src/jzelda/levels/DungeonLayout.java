package jzelda.levels;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import jzelda.entities.Direction;

/**
 * Spatial topology of a dungeon represented as a fixed matrix.
 *
 * <p>
 * The matrix describes room ids placed in map coordinates (row/column). Cell
 * values can be {@code null} to represent no existing room.
 * </p>
 *
 * <p>
 * This structure separates:
 * <ul>
 * <li>topology (which rooms exist and their positions)</li>
 * <li>door rules (defined by {@link LevelExit})</li>
 * </ul>
 * </p>
 */
public final class DungeonLayout {
    private static final String GRID_PREFIX = "cell.";
    private final String dungeonId;
    private final String[][] roomIds;
    private final int rows;
    private final int columns;
    private final int startRow;
    private final int startColumn;
    private int currentRow;
    private int currentColumn;
    private final Map<String, RoomPosition> roomPositions;

    private DungeonLayout(String dungeonId, String[][] roomIds, int rows, int cols, int startRow, int startCol) {
        this.dungeonId = Objects.requireNonNull(dungeonId, "dungeonId cannot be null");
        this.roomIds = roomIds;
        this.rows = rows;
        this.columns = cols;
        this.startRow = startRow;
        this.startColumn = startCol;
        this.currentRow = startRow;
        this.currentColumn = startCol;
        this.roomPositions = buildRoomPositions(roomIds);
    }

    /** Creates a layout from a raw matrix and metadata. */
    public static DungeonLayout of(String dungeonId, String[][] roomIds, int rows, int columns, int startRow, int startColumn) {
        return new DungeonLayout(dungeonId, roomIds, rows, columns, startRow, startColumn);
    }

    /**
     * Builds a position map for efficient room-id lookup.
     *
     * @param matrix room matrix
     * @return room position map
     */
    private static Map<String, RoomPosition> buildRoomPositions(String[][] matrix) {
        Map<String, RoomPosition> map = new HashMap<>();
        for (int r = 0; r < matrix.length; r++) {
            for (int c = 0; c < matrix[r].length; c++) {
                String roomId = matrix[r][c];
                if (roomId != null) {
                    map.put(roomId, new RoomPosition(r, c));
                }
            }
        }
        return map;
    }

    /** @return dungeon logical identifier (e.g. {@code level1}) */
    public String getDungeonId() {
        return dungeonId;
    }

    /** @return grid rows */
    public int getRows() {
        return rows;
    }

    /** @return grid columns */
    public int getColumns() {
        return columns;
    }

    /** @return current player room row in the dungeon matrix. */
    public int getCurrentRow() {
        return currentRow;
    }

    /** @return current player room column in the dungeon matrix. */
    public int getCurrentColumn() {
        return currentColumn;
    }

    /** @return configured starting room row. */
    public int getStartRow() {
        return startRow;
    }

    /** @return configured starting room column. */
    public int getStartColumn() {
        return startColumn;
    }

    /** @return current room id from matrix coordinates. */
    public String getCurrentRoomId() {
        return roomIds[currentRow][currentColumn];
    }

    /** Resets current position to start cell. */
    public void reset() {
        currentRow = startRow;
        currentColumn = startColumn;
    }

    /** Returns true when coordinates point to a declared room. */
    public boolean hasRoom(int row, int column) {
        return isInsideBounds(row, column) && roomIds[row][column] != null;
    }

    /** Returns the room id at coordinates, if present. */
    public Optional<String> getRoomId(int row, int column) {
        if (!isInsideBounds(row, column)) {
            return Optional.empty();
        }
        return Optional.ofNullable(roomIds[row][column]);
    }

    /** Returns the adjacent room id in the given direction, if present. */
    public Optional<String> getAdjacentRoom(Direction direction) {
        int nextRow = currentRow + deltaRow(direction);
        int nextColumn = currentColumn + deltaColumn(direction);
        return getRoomId(nextRow, nextColumn);
    }

    /**
     * Returns the room id adjacent to the given matrix position, if present.
     * This method is structural and does NOT change the internal current position.
     */
    public Optional<String> getAdjacentRoom(int row, int column, Direction direction) {
        int nextRow = row + deltaRow(direction);
        int nextColumn = column + deltaColumn(direction);
        return getRoomId(nextRow, nextColumn);
    }

    /** Returns true when movement is within bounds and target room exists. */
    public boolean canMove(Direction direction) {
        return getAdjacentRoom(direction).isPresent();
    }

    /**
     * Moves current position one cell in the given direction. No validation is
     * performed here.
     */
    public void move(Direction direction) {
        currentRow += deltaRow(direction);
        currentColumn += deltaColumn(direction);
    }

    /** @return row/col for room id if declared in this dungeon, else empty. */
    public Optional<RoomPosition> findRoom(String roomId) {
        if (roomId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(roomPositions.get(roomId));
    }

    /** Ensures matrix is 0..rows and 0..columns bounds check. */
    private boolean isInsideBounds(int row, int column) {
        return row >= 0 && row < rows && column >= 0 && column < columns;
    }

    private int deltaRow(Direction direction) {
        switch (direction) {
        case UP:
            return -1;
        case DOWN:
            return 1;
        default:
            return 0;
        }
    }

    private int deltaColumn(Direction direction) {
        switch (direction) {
        case LEFT:
            return -1;
        case RIGHT:
            return 1;
        default:
            return 0;
        }
    }

    /**
     * Parses room position from LevelExit-style keys (e.g. {@code cell.0.1}).
     *
     * @param propertyName key
     * @return parsed grid key in "row.col" format
     */
    public static String[] parseCellCoordinates(String propertyName) {
        String tail = propertyName.substring(GRID_PREFIX.length());
        String[] parts = tail.split("\\.");
        if (parts.length < 2) {
            throw new IllegalStateException("Configurazione dungeon non valida: " + propertyName);
        }
        return new String[] { parts[0].trim().toLowerCase(Locale.ROOT), parts[1].trim().toLowerCase(Locale.ROOT) };
    }

    /** @return list of IDs currently declared in layout (helpful for validation). */
    public java.util.Set<String> declaredRooms() {
        return roomPositions.keySet().stream().collect(Collectors.toSet());
    }

    /** Moves current coordinates to a room by id. Returns false if room missing. */
    public boolean moveTo(String roomId) {
        RoomPosition position = roomPositions.get(roomId);
        if (position == null) {
            return false;
        }
        currentRow = position.row();
        currentColumn = position.column();
        return true;
    }
}
