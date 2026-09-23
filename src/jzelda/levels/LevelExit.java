package jzelda.levels;

import java.awt.geom.Rectangle2D;
import jzelda.entities.Direction;

/**
 * Immutable definition of a single room transition.
 *
 * <p>
 * A level may expose multiple exits; each one is identified by an id and contains
 * both the target level and the target exit identifier.
 * </p>
 */
public final class LevelExit {
    private final String id;
    private final Rectangle2D.Double bounds;
    private final Direction direction;
    private final String targetLevelId;
    private final String targetExitId;
    private final ExitType type;
    private final boolean requiresKey;
    private final boolean requiresEnemiesDefeated;
    private final boolean initiallyLocked;
    private final String doorStateId;
    private final String visualId;
    private final int mapRow;
    private final int mapColumn;

    /**
     * Creates an immutable room exit definition.
     *
     * @param id exit identifier
     * @param bounds rectangular trigger area
     * @param direction exit side with respect to current level
     * @param targetLevelId identifier of the target level
     * @param targetExitId identifier of the target exit in destination level
     * @param type transition type
     * @param requiresKey whether a key is required
     * @param requiresEnemiesDefeated whether all enemies must be defeated
     */
    public LevelExit(String id, Rectangle2D.Double bounds, Direction direction, String targetLevelId,
            String targetExitId, ExitType type, boolean requiresKey, boolean requiresEnemiesDefeated) {
        this(id, bounds, direction, targetLevelId, targetExitId, type, requiresKey, requiresEnemiesDefeated,
            false, null, null, -1, -1);
        }

        /** Creates an exit with locked-door and rendering metadata. */
        public LevelExit(String id, Rectangle2D.Double bounds, Direction direction, String targetLevelId,
            String targetExitId, ExitType type, boolean requiresKey, boolean requiresEnemiesDefeated,
            boolean initiallyLocked, String doorStateId, String visualId, int mapRow, int mapColumn) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Exit id cannot be null/empty");
        }
        if (bounds == null) {
            throw new IllegalArgumentException("Exit bounds cannot be null");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Exit direction cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Exit type cannot be null");
        }
        this.id = id;
        this.bounds = bounds;
        this.direction = direction;
        this.targetLevelId = targetLevelId;
        this.targetExitId = targetExitId;
        this.type = type;
        this.requiresKey = requiresKey;
        this.requiresEnemiesDefeated = requiresEnemiesDefeated;
        this.initiallyLocked = initiallyLocked;
        this.doorStateId = doorStateId;
        this.visualId = visualId;
        this.mapRow = mapRow;
        this.mapColumn = mapColumn;
    }

    /** @return logical id */
    public String getId() {
        return id;
    }

    /** @return collision bounds used to trigger the transition */
    public Rectangle2D.Double getBounds() {
        return bounds;
    }

    /** @return side of the current room where the exit sits */
    public Direction getDirection() {
        return direction;
    }

    /** @return target level identifier, when applicable */
    public String getTargetLevelId() {
        return targetLevelId;
    }

    /** @return destination exit id inside target level, when applicable */
    public String getTargetExitId() {
        return targetExitId;
    }

    /** @return transition type */
    public ExitType getType() {
        return type;
    }

    /** @return true when key consumption is required to use this exit */
    public boolean isRequiresKey() {
        return requiresKey;
    }

    /** @return true when all enemies must be defeated to use this exit */
    public boolean isRequiresEnemiesDefeated() {
        return requiresEnemiesDefeated;
    }

    public boolean isInitiallyLocked() {
        return initiallyLocked;
    }

    public String getDoorStateId() {
        return doorStateId;
    }

    public String getVisualId() {
        return visualId;
    }

    public int getMapRow() {
        return mapRow;
    }

    public int getMapColumn() {
        return mapColumn;
    }

    /**
     * Returns true only for the original compatibility exits used to complete a level.
     * Dungeon room transitions are valid ROOM exits even when target ids are omitted and
     * are resolved implicitly from the room topology.
     */
    public boolean isLegacy() {
        return type == ExitType.LEVEL_COMPLETE;
    }
}
