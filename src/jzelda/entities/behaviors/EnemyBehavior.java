package jzelda.entities.behaviors;

import jzelda.entities.Enemy;
import jzelda.model.GameModel;

/**
 * Strategy interface for enemy artificial intelligence. Concrete strategies can
 * patrol, chase, wander or shoot without changing the enemy base class.
 */
public interface EnemyBehavior {
    /**
     * Updates the enemy for one frame.
     *
     * @param enemy enemy controlled by this strategy
     * @param model current game model
     * @param deltaMs elapsed milliseconds
     */
    void update(Enemy enemy, GameModel model, long deltaMs);

    /**
     * @return short behavior name for debugging and documentation
     */
    String getName();
}
