package jzelda.levels;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import jzelda.entities.Direction;

/** Resolves room background sprite names from their canonical open-door suffix. */
public final class RoomSpriteResolver {
    private static final Direction[] CANONICAL_ORDER = {
            Direction.UP, Direction.RIGHT, Direction.DOWN, Direction.LEFT
    };

    private RoomSpriteResolver() {
    }

    /**
     * Adds a newly opened direction to the state encoded in a room sprite name.
     *
     * @param currentSpriteName current base or variant sprite name
     * @param newlyOpenedDirection direction to add
     * @return canonical resulting sprite name
     */
    public static String resolveSpriteAfterOpeningDoor(String currentSpriteName, Direction newlyOpenedDirection) {
        if (currentSpriteName == null || currentSpriteName.trim().isEmpty()) {
            throw new IllegalArgumentException("Room sprite name cannot be empty");
        }
        String normalized = currentSpriteName.trim().toLowerCase(Locale.ROOT);
        int suffixIndex = normalized.indexOf("_open_");
        String baseRoomId = suffixIndex < 0 ? normalized : normalized.substring(0, suffixIndex);
        EnumSet<Direction> openDirections = extractOpenDirections(normalized, suffixIndex);
        if (newlyOpenedDirection != null && newlyOpenedDirection != Direction.NONE) {
            openDirections.add(newlyOpenedDirection);
        }
        return buildSpriteName(baseRoomId, openDirections);
    }

    /** Extracts directions encoded by the _open_ suffix. */
    public static EnumSet<Direction> extractOpenDirections(String spriteName) {
        if (spriteName == null) {
            return EnumSet.noneOf(Direction.class);
        }
        String normalized = spriteName.trim().toLowerCase(Locale.ROOT);
        return extractOpenDirections(normalized, normalized.indexOf("_open_"));
    }

    private static EnumSet<Direction> extractOpenDirections(String normalized, int suffixIndex) {
        EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);
        if (suffixIndex < 0) {
            return directions;
        }
        String suffix = normalized.substring(suffixIndex + "_open_".length());
        for (String token : suffix.split("_")) {
            Direction direction = parseDirection(token);
            if (direction != null) {
                directions.add(direction);
            }
        }
        return directions;
    }

    /** Builds a canonical sprite name from a base room id and open directions. */
    public static String buildSpriteName(String baseRoomId, Set<Direction> openDirections) {
        String base = baseRoomId == null ? "" : baseRoomId.trim().toLowerCase(Locale.ROOT);
        if (openDirections == null || openDirections.isEmpty()) {
            return base;
        }
        StringBuilder result = new StringBuilder(base).append("_open");
        for (Direction direction : CANONICAL_ORDER) {
            if (openDirections.contains(direction)) {
                result.append('_').append(direction.name().toLowerCase(Locale.ROOT));
            }
        }
        return result.toString();
    }

    private static Direction parseDirection(String token) {
        switch (token) {
        case "up":
            return Direction.UP;
        case "right":
            return Direction.RIGHT;
        case "down":
            return Direction.DOWN;
        case "left":
            return Direction.LEFT;
        default:
            return null;
        }
    }
}
