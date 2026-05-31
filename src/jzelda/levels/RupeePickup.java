package jzelda.levels;

import java.awt.geom.Rectangle2D;

/**
 * Collectible currency placed inside levels.
 */
public class RupeePickup {
    private final double x;
    private final double y;
    private final int value;
    private boolean collected;

    /**
     * Creates a rupee pickup.
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param value value in rupees
     */
    public RupeePickup(double x, double y, int value) {
        this.x = x;
        this.y = y;
        this.value = value;
    }

    /**
     * @return x coordinate
     */
    public double getX() {
        return x;
    }

    /**
     * @return y coordinate
     */
    public double getY() {
        return y;
    }

    /**
     * @return rupee value
     */
    public int getValue() {
        return value;
    }

    /**
     * @return collision bounds
     */
    public Rectangle2D.Double getBounds() {
        return new Rectangle2D.Double(x + 8, y + 8, 16, 16);
    }

    /**
     * @return {@code true} after collection
     */
    public boolean isCollected() {
        return collected;
    }

    /**
     * Marks this rupee as collected.
     */
    public void collect() {
        collected = true;
    }
}
