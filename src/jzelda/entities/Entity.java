package jzelda.entities;

import java.awt.geom.Rectangle2D;

/**
 * Base class for all active objects in the model world. It stores only logical
 * state; rendering is performed by the view.
 */
public abstract class Entity {
    private double x;
    private double y;
    private final int width;
    private final int height;
    private int health;
    private boolean alive = true;
    private String spriteKey;

    /**
     * Creates an entity with a rectangle and health value.
     *
     * @param x initial x coordinate
     * @param y initial y coordinate
     * @param width width in pixels
     * @param height height in pixels
     * @param health starting health points
     * @param spriteKey logical sprite identifier used by the view
     */
    protected Entity(double x, double y, int width, int height, int health, String spriteKey) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.health = health;
        this.spriteKey = spriteKey;
    }

    /**
     * @return current x coordinate
     */
    public double getX() {
        return x;
    }

    /**
     * @return current y coordinate
     */
    public double getY() {
        return y;
    }

    /**
     * Moves the entity to an absolute position.
     *
     * @param x new x coordinate
     * @param y new y coordinate
     */
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Applies a relative movement.
     *
     * @param dx horizontal delta in pixels
     * @param dy vertical delta in pixels
     */
    public void translate(double dx, double dy) {
        x += dx;
        y += dy;
    }

    /**
     * @return width in pixels
     */
    public int getWidth() {
        return width;
    }

    /**
     * @return height in pixels
     */
    public int getHeight() {
        return height;
    }

    /**
     * @return current health points
     */
    public int getHealth() {
        return health;
    }

    /**
     * Sets health and marks the entity dead when it reaches zero.
     *
     * @param health new health value
     */
    public void setHealth(int health) {
        this.health = Math.max(0, health);
        if (this.health == 0) {
            alive = false;
        }
    }

    /**
     * Damages this entity.
     *
     * @param amount damage amount in health points
     */
    public void damage(int amount) {
        if (amount > 0) {
            setHealth(health - amount);
        }
    }

    /**
     * Restores health points while preserving alive state when health becomes
     * positive.
     *
     * @param amount amount to heal
     */
    public void heal(int amount) {
        if (amount > 0) {
            health += amount;
            if (health > 0) {
                alive = true;
            }
        }
    }

    /**
     * @return {@code true} if the entity participates in gameplay
     */
    public boolean isAlive() {
        return alive;
    }

    /**
     * Changes the alive flag.
     *
     * @param alive new alive flag
     */
    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    /**
     * @return logical sprite key
     */
    public String getSpriteKey() {
        return spriteKey;
    }

    /**
     * Changes the sprite key used by the view.
     *
     * @param spriteKey new sprite key
     */
    public void setSpriteKey(String spriteKey) {
        this.spriteKey = spriteKey;
    }

    /**
     * @return collision rectangle in world coordinates
     */
    public Rectangle2D.Double getBounds() {
        return new Rectangle2D.Double(x, y, width, height);
    }
}
