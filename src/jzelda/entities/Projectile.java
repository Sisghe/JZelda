package jzelda.entities;

/**
 * Simple enemy projectile. It belongs to a level and is updated by the model.
 */
public class Projectile extends Entity {
    private final double dx;
    private final double dy;
    private final int damage;
    private long lifeMs = 2500;

    /**
     * Creates a projectile.
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param dx horizontal velocity in pixels per frame
     * @param dy vertical velocity in pixels per frame
     * @param damage damage on contact
     */
    public Projectile(double x, double y, double dx, double dy, int damage) {
        super(x, y, 10, 10, 1, "projectile");
        this.dx = dx;
        this.dy = dy;
        this.damage = damage;
    }

    /**
     * Advances the projectile.
     *
     * @param deltaMs elapsed milliseconds
     */
    public void update(long deltaMs) {
        translate(dx, dy);
        lifeMs -= Math.max(0, deltaMs);
        if (lifeMs <= 0) {
            setAlive(false);
        }
    }

    /**
     * @return projectile damage
     */
    public int getDamage() {
        return damage;
    }
}
