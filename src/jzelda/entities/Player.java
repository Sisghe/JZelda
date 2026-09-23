package jzelda.entities;

import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import jzelda.model.GameConfig;

/**
 * Player model object. It stores combat stats, inventory, animation counters,
 * equipped weapon state and temporary status effects. Input and rendering are
 * handled elsewhere.
 */
public class Player extends Entity {
    private static final int BASE_SPEED = 4;
    private static final int UNARMED_ATTACK_REACH = 18;
    private static final int SWORD_ATTACK_REACH = 30;
    private static final int UNARMED_ATTACK_DAMAGE = 1;
    private static final int SWORD_ATTACK_DAMAGE = 2;

    private int maxHealth = 6;
    private int lives = GameConfig.STARTING_LIVES;
    private int rupees;
    private Direction direction = Direction.DOWN;
    private boolean swordEquipped;
    private long attackCooldownMs;
    private long invulnerableMs;
    private int shieldBlocksRemaining;
    private long speedBoostMs;
    private long frameClockMs;
    private final Map<String, Integer> inventory = new LinkedHashMap<>();

    /** Pixel di margine orizzontale usati dalla hitbox di movimento. */
    private static final int COLLISION_LEFT_INSET = 4;
    private static final int COLLISION_RIGHT_INSET = 4;
    /** Margini verticali: lascia visibili cappello/capo e limita l'invasione del muro con il corpo. */
    private static final int COLLISION_TOP_INSET = 20;
    private static final int COLLISION_BOTTOM_INSET = 0;

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
        speedBoostMs = Math.max(0, speedBoostMs - deltaMs);
        frameClockMs += Math.max(0, deltaMs);
    }

    /** Restores all health points and clears temporary damage immunity. */
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

    /** Decrements life count when the player dies. */
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

    /** Returns the short interaction area immediately in front of the player. */
    public Rectangle2D.Double getInteractionBounds() {
        Rectangle2D.Double body = getCollisionBounds();
        if (direction == Direction.UP) {
            return new Rectangle2D.Double(body.x, body.y - 8, body.width, 8);
        }
        if (direction == Direction.DOWN) {
            return new Rectangle2D.Double(body.x, body.getMaxY(), body.width, 8);
        }
        if (direction == Direction.LEFT) {
            return new Rectangle2D.Double(body.x - 8, body.y, 8, body.height);
        }
        if (direction == Direction.RIGHT) {
            return new Rectangle2D.Double(body.getMaxX(), body.y, 8, body.height);
        }
        return body;
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

    /** Starts the attack cooldown. */
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

    /** @return {@code true} if the permanent shield still has blocks remaining */
    public boolean hasShield() {
        return shieldBlocksRemaining > 0;
    }

    /**
     * Activates the shield effect.
     *
     * @param durationMs duration in milliseconds
     */
    public void equipShield() {
        if (shieldBlocksRemaining <= 0) {
            shieldBlocksRemaining = 3;
        }
    }

    /** Consumes one shield block when the threat is directly in front. */
    public boolean parryHit(Direction sourceDirection) {
        if (!hasShield() || sourceDirection == null || sourceDirection != direction) {
            return false;
        }
        shieldBlocksRemaining--;
        return true;
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
     * Equips the sword permanently for the current run.
     */
    public void equipSword() {
        swordEquipped = true;
    }

    /**
     * @return {@code true} when the sword has been found and equipped
     */
    public boolean hasSwordEquipped() {
        return swordEquipped;
    }

    /**
     * @return attack damage based on the currently equipped weapon
     */
    public int getAttackDamage() {
        return swordEquipped ? SWORD_ATTACK_DAMAGE : UNARMED_ATTACK_DAMAGE;
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
     * {@inheritDoc}
     */
    @Override
    public Rectangle2D.Double getCollisionBounds() {
        return new Rectangle2D.Double(
                getX() + COLLISION_LEFT_INSET,
                getY() + COLLISION_TOP_INSET,
                getWidth() - COLLISION_LEFT_INSET - COLLISION_RIGHT_INSET,
                getHeight() - COLLISION_TOP_INSET - COLLISION_BOTTOM_INSET);
    }

    /**
     * @return rectangle representing the current attack area
     */
    public Rectangle2D.Double getAttackBounds() {
        int reach = swordEquipped ? SWORD_ATTACK_REACH : UNARMED_ATTACK_REACH;
        Rectangle2D.Double body = getCollisionBounds();
        if (direction == Direction.UP) {
            return new Rectangle2D.Double(body.x, body.y - reach, body.width, reach);
        }
        if (direction == Direction.DOWN) {
            return new Rectangle2D.Double(body.x, body.y + body.height, body.width, reach);
        }
        if (direction == Direction.LEFT) {
            return new Rectangle2D.Double(body.x - reach, body.y, reach, body.height);
        }
        if (direction == Direction.RIGHT) {
            return new Rectangle2D.Double(body.x + body.width, body.y, reach, body.height);
        }
        return new Rectangle2D.Double(body.x, body.y, body.width, body.height);
    }
}
