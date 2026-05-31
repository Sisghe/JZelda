package jzelda.entities;

import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jzelda.model.GameConfig;

/**
 * Player model object. It stores combat stats, inventory, animation counters and
 * temporary status effects. Input and rendering are handled elsewhere.
 */
public class Player extends Entity {
    private static final int BASE_SPEED = 4;
    private int maxHealth = 6;
    private int lives = GameConfig.STARTING_LIVES;
    private int rupees;
    private Direction direction = Direction.DOWN;
    private long attackCooldownMs;
    private long invulnerableMs;
    private long shieldMs;
    private long speedBoostMs;
    private long frameClockMs;
    private final Map<String, Integer> inventory = new LinkedHashMap<>();

    /**
     * Creates a player at the given position.
     *
     * @param x initial x coordinate
     * @param y initial y coordinate
     */
    public Player(double x, double y) {
        super(x, y, 24, 24, 6, "player");
    }

    /**
     * Advances cooldowns and animation timers.
     *
     * @param deltaMs elapsed milliseconds since the last update
     */
    public void updateTimers(long deltaMs) {
        attackCooldownMs = Math.max(0, attackCooldownMs - deltaMs);
        invulnerableMs = Math.max(0, invulnerableMs - deltaMs);
        shieldMs = Math.max(0, shieldMs - deltaMs);
        speedBoostMs = Math.max(0, speedBoostMs - deltaMs);
        frameClockMs += Math.max(0, deltaMs);
    }

    /**
     * Restores all health points and clears temporary damage immunity.
     */
    public void restoreFullHealth() {
        setHealth(maxHealth);
        invulnerableMs = 0;
    }

    /**
     * @return maximum health points
     */
    public int getMaxHealth() {
        return maxHealth;
    }

    /**
     * Increases maximum health and heals the player by the same amount.
     *
     * @param amount health increment
     */
    public void increaseMaxHealth(int amount) {
        if (amount > 0) {
            maxHealth += amount;
            heal(amount);
        }
    }

    /**
     * @return remaining lives for the current run
     */
    public int getLives() {
        return lives;
    }

    /**
     * Sets the number of lives.
     *
     * @param lives new life count
     */
    public void setLives(int lives) {
        this.lives = Math.max(0, lives);
    }

    /**
     * Decrements life count when the player dies.
     */
    public void loseLife() {
        lives = Math.max(0, lives - 1);
    }

    /**
     * @return current rupee balance
     */
    public int getRupees() {
        return rupees;
    }

    /**
     * Adds rupees to the balance.
     *
     * @param amount amount to add
     */
    public void addRupees(int amount) {
        if (amount > 0) {
            rupees += amount;
        }
    }

    /**
     * Spends rupees if the player can afford the cost.
     *
     * @param amount amount to spend
     * @return {@code true} when the transaction succeeded
     */
    public boolean spendRupees(int amount) {
        if (amount <= rupees) {
            rupees -= Math.max(0, amount);
            return true;
        }
        return false;
    }

    /**
     * @return current movement and attack direction
     */
    public Direction getDirection() {
        return direction;
    }

    /**
     * Updates current direction, ignoring {@link Direction#NONE}.
     *
     * @param direction new direction
     */
    public void setDirection(Direction direction) {
        if (direction != null && direction != Direction.NONE) {
            this.direction = direction;
        }
    }

    /**
     * @return current speed after temporary boosts
     */
    public int getSpeed() {
        return speedBoostMs > 0 ? BASE_SPEED + 2 : BASE_SPEED;
    }

    /**
     * @return {@code true} if the player may launch another attack
     */
    public boolean canAttack() {
        return attackCooldownMs <= 0;
    }

    /**
     * Starts the attack cooldown.
     */
    public void resetAttackCooldown() {
        attackCooldownMs = 280;
    }

    /**
     * @return {@code true} if the player is temporarily immune to damage
     */
    public boolean isInvulnerable() {
        return invulnerableMs > 0;
    }

    /**
     * Grants short damage immunity.
     *
     * @param durationMs duration in milliseconds
     */
    public void grantInvulnerability(long durationMs) {
        invulnerableMs = Math.max(invulnerableMs, durationMs);
    }

    /**
     * @return {@code true} if a shield effect is active
     */
    public boolean hasShield() {
        return shieldMs > 0;
    }

    /**
     * Activates the shield effect.
     *
     * @param durationMs duration in milliseconds
     */
    public void activateShield(long durationMs) {
        shieldMs = Math.max(shieldMs, durationMs);
    }

    /**
     * Activates a temporary speed boost.
     *
     * @param durationMs duration in milliseconds
     */
    public void activateSpeedBoost(long durationMs) {
        speedBoostMs = Math.max(speedBoostMs, durationMs);
    }

    /**
     * Adds an inventory item count.
     *
     * @param key inventory key
     * @param amount amount to add
     */
    public void addInventory(String key, int amount) {
        inventory.merge(key, amount, Integer::sum);
    }

    /**
     * Consumes an inventory item count.
     *
     * @param key inventory key
     * @param amount amount to consume
     * @return {@code true} when enough items were available
     */
    public boolean consumeInventory(String key, int amount) {
        int current = inventory.getOrDefault(key, 0);
        if (current >= amount) {
            if (current == amount) {
                inventory.remove(key);
            } else {
                inventory.put(key, current - amount);
            }
            return true;
        }
        return false;
    }

    /**
     * @return immutable inventory view
     */
    public Map<String, Integer> getInventory() {
        return Collections.unmodifiableMap(inventory);
    }

    /**
     * @return 0/1 animation frame derived from elapsed time
     */
    public int getAnimationFrame() {
        return (int) ((frameClockMs / 180) % 2);
    }

    /**
     * @return rectangle representing the current sword attack
     */
    public Rectangle2D.Double getAttackBounds() {
        int reach = 24;
        Rectangle2D.Double body = getBounds();
        switch (direction) {
        case UP:
            return new Rectangle2D.Double(body.x, body.y - reach, body.width, reach);
        case DOWN:
            return new Rectangle2D.Double(body.x, body.y + body.height, body.width, reach);
        case LEFT:
            return new Rectangle2D.Double(body.x - reach, body.y, reach, body.height);
        case RIGHT:
            return new Rectangle2D.Double(body.x + body.width, body.y, reach, body.height);
        default:
            return new Rectangle2D.Double(body.x, body.y, body.width, body.height);
        }
    }
}
