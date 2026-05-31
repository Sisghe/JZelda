package jzelda.entities;

import jzelda.entities.behaviors.RandomShooterBehavior;

/**
 * Enemy that wanders and periodically fires projectiles.
 */
public class ShooterEnemy extends Enemy {
    /**
     * Creates a shooter enemy at the specified map position.
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    public ShooterEnemy(double x, double y) {
        super(x, y, 3, "enemy_shooter", new RandomShooterBehavior());
        setContactDamage(2);
    }
}
