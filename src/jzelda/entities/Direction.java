package jzelda.entities;

/**
 * Cardinal directions used for movement, attacks and sprite orientation.
 */
public enum Direction {
    /** Upward movement. */
    UP(0, -1),
    /** Downward movement. */
    DOWN(0, 1),
    /** Left movement. */
    LEFT(-1, 0),
    /** Right movement. */
    RIGHT(1, 0),
    /** No movement. */
    NONE(0, 0);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    /**
     * @return horizontal step component
     */
    public int dx() {
        return dx;
    }

    /**
     * @return vertical step component
     */
    public int dy() {
        return dy;
    }

    /**
     * Returns the opposite direction on the same axis.
     *
     * @return opposite direction
     */
    public Direction opposite() {
        if (this == UP) {
            return DOWN;
        }
        if (this == DOWN) {
            return UP;
        }
        if (this == LEFT) {
            return RIGHT;
        }
        if (this == RIGHT) {
            return LEFT;
        }
        return NONE;
    }
}
