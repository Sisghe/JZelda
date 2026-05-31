package jzelda.entities.behaviors;

import jzelda.entities.Enemy;
import jzelda.entities.Player;
import jzelda.model.GameModel;

/**
 * Strategy that patrols horizontally and chases the player when they are close.
 */
public class ChasePatrolBehavior implements EnemyBehavior {
    private int patrolDirection = 1;

    @Override
    public void update(Enemy enemy, GameModel model, long deltaMs) {
        Player player = model.getPlayer();
        double speed = 1.7;
        double dx = 0;
        double dy = 0;
        double distance = enemy.getBounds().getCenterX() - player.getBounds().getCenterX();
        double vertical = enemy.getBounds().getCenterY() - player.getBounds().getCenterY();
        if (Math.abs(distance) < 128 && Math.abs(vertical) < 96) {
            dx = distance > 0 ? -speed : speed;
            dy = vertical > 0 ? -speed : speed;
        } else {
            dx = patrolDirection * speed;
        }
        boolean moved = model.moveEnemyWithCollision(enemy, dx, dy);
        if (!moved && Math.abs(dy) < 0.1) {
            patrolDirection *= -1;
        }
        if (enemy.getBounds().intersects(player.getBounds())) {
            model.damagePlayer(enemy.getContactDamage(), "Contatto con un guardiano");
        }
    }

    @Override
    public String getName() {
        return "ChasePatrol";
    }
}
