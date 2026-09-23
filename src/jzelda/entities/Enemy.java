package jzelda.entities;

import java.awt.geom.Rectangle2D;
import jzelda.entities.behaviors.EnemyBehavior;
import jzelda.model.GameConfig;
import jzelda.model.GameModel;

/**
 * Abstract model for enemies. The movement logic is delegated to a Strategy so
 * that different enemy types remain easy to extend. It also keeps directional
 * orientation and a simple melee attack state used by the Reaper implementation.
 */
public abstract class Enemy extends Entity {
    private EnemyBehavior behavior;
    private int contactDamage = 1;
    private long frameClockMs;

    // Orientation and movement state
    private Direction direction = Direction.DOWN;
    private boolean moving = false;

    // Melee attack state
    private boolean attacking = false;
    private long attackClockMs = 0L;
    private boolean attackDamageApplied = false;
    private long attackCooldownMs = 0L;

    // Reaper attack constants (model-side)
    public static final int ATTACK_FRAME_COUNT = 7;
    public static final long ATTACK_FRAME_DURATION_MS = 80L;
    public static final long ATTACK_COOLDOWN_MS = 1000L;
    public static final int ATTACK_IMPACT_FRAME = 3;

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
        long dt = Math.max(0, deltaMs);
        frameClockMs += dt;

        // advance attack timers and cooldown
        if (attackCooldownMs > 0) {
            attackCooldownMs = Math.max(0, attackCooldownMs - dt);
        }
        if (attacking) {
            attackClockMs += dt;
            // Attack animation advances here. Damage must be applied by the
            // behavior at the impact frame using applyMeleeDamageIfNeeded(model),
            // so we do not mark damage as applied inside the model update.
            if (attackClockMs >= ATTACK_FRAME_COUNT * ATTACK_FRAME_DURATION_MS) {
                // attack animation finished
                attacking = false;
                attackClockMs = 0L;
                attackCooldownMs = ATTACK_COOLDOWN_MS;
                attackDamageApplied = false; // reset for next attack
            }
        }

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

    /** Direction accessors used by view and behaviors */
    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        if (direction != null) {
            this.direction = direction;
        }
    }

    public boolean isMoving() {
        return moving;
    }

    public void setMoving(boolean moving) {
        this.moving = moving;
    }

    /** Attack state accessors */
    public boolean isAttacking() {
        return attacking;
    }

    public boolean isAttackDamageApplied() {
        return attackDamageApplied;
    }

    public void startAttack() {
        if (attacking || attackCooldownMs > 0) {
            return;
        }
        this.attacking = true;
        this.attackClockMs = 0L;
        this.attackDamageApplied = false;
    }

    public int getAttackFrame() {
        if (!attacking) {
            return 0;
        }
        return (int) Math.min(ATTACK_FRAME_COUNT - 1, attackClockMs / ATTACK_FRAME_DURATION_MS);
    }

    public long getAttackCooldownMs() {
        return attackCooldownMs;
    }

    /** Returns the melee attack rectangle one tile in front of the enemy */
    public Rectangle2D.Double getMeleeAttackBounds() {
        Rectangle2D.Double body = getCollisionBounds();
        double tx = body.x;
        double ty = body.y;
        double tw = GameConfig.TILE_SIZE;
        double th = GameConfig.TILE_SIZE;
        switch (direction) {
        case UP:
            tx = body.x;
            ty = body.y - th;
            tw = th = GameConfig.TILE_SIZE;
            break;
        case DOWN:
            tx = body.x;
            ty = body.y + body.height;
            tw = th = GameConfig.TILE_SIZE;
            break;
        case LEFT:
            tx = body.x - tw;
            ty = body.y;
            tw = th = GameConfig.TILE_SIZE;
            break;
        case RIGHT:
            tx = body.x + body.width;
            ty = body.y;
            tw = th = GameConfig.TILE_SIZE;
            break;
        default:
            break;
        }
        return new Rectangle2D.Double(tx, ty, tw, th);
    }

    /** When a reaper attack impact frame is reached, behavior calls this to apply damage once */
    public boolean applyMeleeDamageIfNeeded(GameModel model) {
        if (attackDamageApplied) {
            return false;
        }
        Player player = model.getPlayer();
        if (player.getCollisionBounds().intersects(getMeleeAttackBounds())) {
            model.damagePlayer(1, "Morso del Reaper", model.directionFromPlayerTo(this));
            attackDamageApplied = true;
            return true;
        }
        attackDamageApplied = true; // mark as checked even if missed
        return false;
    }
}

