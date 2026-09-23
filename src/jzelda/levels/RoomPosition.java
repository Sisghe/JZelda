package jzelda.levels;

/**
 * Immutable coordinate pair inside a dungeon grid.
 */
public final class RoomPosition {
    private final int row;
    private final int column;

    /**
     * Creates a position.
     *
     * @param row grid row
     * @param column grid column
     */
    public RoomPosition(int row, int column) {
        this.row = row;
        this.column = column;
    }

    /** @return row index */
    public int row() {
        return row;
    }

    /** @return column index */
    public int column() {
        return column;
    }

    @Override
    public String toString() {
        return "RoomPosition{row=" + row + ", column=" + column + '}';
    }
}
