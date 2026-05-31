package jzelda.entities;

/**
 * Factory Method creator for concrete enemy entities used by level loading.
 */
public class EntityFactory {
    /**
     * Creates an enemy from a map code.
     *
     * @param code map code, for example {@code A} or {@code W}
     * @param x x coordinate
     * @param y y coordinate
     * @return enemy instance
     * @throws IllegalArgumentException when the code is unknown
     */
    public Enemy createEnemy(char code, double x, double y) {
        switch (code) {
        case 'A':
            return new PatrolEnemy(x, y);
        case 'W':
            return new ShooterEnemy(x, y);
        default:
            throw new IllegalArgumentException("Codice nemico non supportato: " + code);
        }
    }
}
