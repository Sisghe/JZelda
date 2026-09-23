package jzelda.entities;

import jzelda.entities.behaviors.ChasePatrolBehavior;

/**
 * Enemy that patrols horizontally and chases the player at short distance.
 */
public class PatrolEnemy extends Enemy {
    /**
     * Creates a patrol enemy at the specified map position.
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    public PatrolEnemy(double x, double y) {
        // Use reaper sprite key; entity factory still creates PatrolEnemy for map code 'A'.
        super(x, y, 2, "enemy_reaper", new ChasePatrolBehavior());
    }
}
