package jzelda.entities;

import jzelda.entities.behaviors.EnemyBehavior;
import jzelda.model.GameModel;

/**
 * Abstract model for enemies. The movement logic is delegated to a Strategy so
 * that different enemy types remain easy to extend.
 */
public abstract class Enemy extends Entity {
    private EnemyBehavior behavior;
    private int contactDamage = 1;
    private long frameClockMs;

    /**
     * Creates an enemy.
     *
     * @param x initial x coordinate
     * @param y initial y coordinate
     * @param health starting health
     * @param spriteKey logical sprite key
     * @param behavior movement/combat strategy
     */
    protected Enemy(double x, double y, int health, String spriteKey, EnemyBehavior behavior) {
        super(x, y, 24, 24, health, spriteKey);
        this.behavior = behavior;
    }

    /**
     * Runs the strategy update and animation timer.
     *
     * @param model game model
     * @param deltaMs elapsed milliseconds
     */
    public void update(GameModel model, long deltaMs) {
        frameClockMs += Math.max(0, deltaMs);
        if (isAlive() && behavior != null) {
            behavior.update(this, model, deltaMs);
        }
    }

    /**
     * @return assigned behavior strategy
     */
    public EnemyBehavior getBehavior() {
        return behavior;
    }

    /**
     * Changes behavior at runtime.
     *
     * @param behavior replacement strategy
     */
    public void setBehavior(EnemyBehavior behavior) {
        this.behavior = behavior;
    }

    /**
     * @return contact damage dealt to the player
     */
    public int getContactDamage() {
        return contactDamage;
    }

    /**
     * Sets contact damage.
     *
     * @param contactDamage damage points
     */
    public void setContactDamage(int contactDamage) {
        this.contactDamage = Math.max(1, contactDamage);
    }

    /**
     * @return 0/1 animation frame
     */
    public int getAnimationFrame() {
        return (int) ((frameClockMs / 220) % 2);
    }
}
