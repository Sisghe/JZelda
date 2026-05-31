package jzelda.levels;

/**
 * Logical tile categories used by collision and rendering.
 */
public enum TileType {
    /** Walkable floor. */
    FLOOR(false),
    /** Solid wall or obstacle. */
    WALL(true),
    /** Exit tile used to finish a level. */
    EXIT(false);

    private final boolean blocked;

    TileType(boolean blocked) {
        this.blocked = blocked;
    }

    /**
     * @return {@code true} when entities cannot pass through the tile
     */
    public boolean isBlocked() {
        return blocked;
    }
}
