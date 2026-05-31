package jzelda.levels;

import java.awt.geom.Rectangle2D;

import jzelda.items.Item;

/**
 * Item positioned inside a level map.
 */
public class ItemDrop {
    private final Item item;
    private final double x;
    private final double y;
    private boolean collected;

    /**
     * Creates a map item drop.
     *
     * @param item item definition
     * @param x x coordinate
     * @param y y coordinate
     */
    public ItemDrop(Item item, double x, double y) {
        this.item = item;
        this.x = x;
        this.y = y;
    }

    /**
     * @return contained item
     */
    public Item getItem() {
        return item;
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
     * @return collision bounds
     */
    public Rectangle2D.Double getBounds() {
        return new Rectangle2D.Double(x + 4, y + 4, 24, 24);
    }

    /**
     * @return {@code true} once the item has been collected
     */
    public boolean isCollected() {
        return collected;
    }

    /**
     * Marks the item as collected.
     */
    public void collect() {
        collected = true;
    }
}
