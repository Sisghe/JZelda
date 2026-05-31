package jzelda.entities.behaviors;

import java.util.Random;

import jzelda.entities.Enemy;
import jzelda.entities.Player;
import jzelda.entities.Projectile;
import jzelda.model.GameModel;

/**
 * Strategy that wanders randomly and shoots toward the player at intervals.
 */
public class RandomShooterBehavior implements EnemyBehavior {
    private final Random random = new Random();
    private long directionMs;
    private long shootMs = 900;
    private double dx = 1.2;
    private double dy = 0;

    @Override
    public void update(Enemy enemy, GameModel model, long deltaMs) {
        directionMs -= deltaMs;
        shootMs -= deltaMs;
        if (directionMs <= 0) {
            directionMs = 600 + random.nextInt(900);
            int choice = random.nextInt(4);
            dx = choice == 0 ? 1.2 : choice == 1 ? -1.2 : 0;
            dy = choice == 2 ? 1.2 : choice == 3 ? -1.2 : 0;
        }
        if (!model.moveEnemyWithCollision(enemy, dx, dy)) {
            dx = -dx;
            dy = -dy;
        }
        Player player = model.getPlayer();
        if (shootMs <= 0 && distanceToPlayer(enemy, player) < 260) {
            shootMs = 1300 + random.nextInt(700);
            double vx = player.getBounds().getCenterX() - enemy.getBounds().getCenterX();
            double vy = player.getBounds().getCenterY() - enemy.getBounds().getCenterY();
            double len = Math.max(1.0, Math.hypot(vx, vy));
            model.spawnProjectile(new Projectile(enemy.getX() + 8, enemy.getY() + 8, vx / len * 4.0, vy / len * 4.0, 1));
        }
        if (enemy.getBounds().intersects(player.getBounds())) {
            model.damagePlayer(enemy.getContactDamage(), "Colpo di creatura errante");
        }
    }

    @Override
    public String getName() {
        return "RandomShooter";
    }

    private double distanceToPlayer(Enemy enemy, Player player) {
        double dx = enemy.getBounds().getCenterX() - player.getBounds().getCenterX();
        double dy = enemy.getBounds().getCenterY() - player.getBounds().getCenterY();
        return Math.hypot(dx, dy);
    }
}
