package jzelda.levels;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jzelda.entities.Direction;

/**
 * Runtime progress for physical dungeon doors. The set is intentionally scoped
 * above individual Level instances so recreated rooms retain opened doors.
 *
 * <p>The identifiers are stable and can be serialized by a future save system.</p>
 */
public final class DungeonProgress {
    private final Set<String> openedDoorIds = new HashSet<>();
    private final Set<String> visitedRooms = new HashSet<>();
    private final Map<String, String> roomSpriteStates = new HashMap<>();

    /** @param doorStateId physical door identifier */
    public boolean isDoorOpen(String doorStateId) {
        return doorStateId != null && openedDoorIds.contains(doorStateId);
    }

    /** @param doorStateId physical door identifier */
    public void openDoor(String doorStateId) {
        if (doorStateId != null && !doorStateId.trim().isEmpty()) {
            openedDoorIds.add(doorStateId.trim());
        }
    }

    /** Clears progress for a new run. */
    public void reset() {
        openedDoorIds.clear();
        visitedRooms.clear();
        roomSpriteStates.clear();
    }

    public void visitRoom(String roomId) {
        if (roomId != null && !roomId.trim().isEmpty()) {
            visitedRooms.add(roomId.trim().toLowerCase());
        }
    }

    public boolean isRoomVisited(String roomId) {
        return roomId != null && visitedRooms.contains(roomId.trim().toLowerCase());
    }

    public Set<String> getVisitedRooms() {
        return Collections.unmodifiableSet(visitedRooms);
    }

    /** Returns the current runtime sprite name, defaulting to the room id. */
    public String getRoomSpriteState(String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) {
            return roomId;
        }
        String normalized = roomId.trim().toLowerCase();
        return roomSpriteStates.getOrDefault(normalized, normalized);
    }

    /** Atomically adds each side of one physical door to both room sprites. */
    public void openDoorForRooms(String currentRoomId, Direction currentDirection,
            String destinationRoomId, Direction destinationDirection) {
        if (currentRoomId == null || destinationRoomId == null) {
            return;
        }
        String current = getRoomSpriteState(currentRoomId);
        String destination = getRoomSpriteState(destinationRoomId);
        roomSpriteStates.put(currentRoomId.trim().toLowerCase(),
                RoomSpriteResolver.resolveSpriteAfterOpeningDoor(current, currentDirection));
        roomSpriteStates.put(destinationRoomId.trim().toLowerCase(),
                RoomSpriteResolver.resolveSpriteAfterOpeningDoor(destination, destinationDirection));
    }

    /** @return read-only room sprite state map */
    public Map<String, String> getRoomSpriteStates() {
        return Collections.unmodifiableMap(roomSpriteStates);
    }

    /** @return read-only opened physical door identifiers */
    public Set<String> getOpenedDoorIds() {
        return Collections.unmodifiableSet(openedDoorIds);
    }
}
