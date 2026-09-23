package jzelda.levels;

/**
 * Type of room/level transition represented by a {@link LevelExit}.
 */
public enum ExitType {
    /**
     * Transition into another room/level without marking progress completion.
     */
    ROOM,
    /**
     * Transition that keeps legacy level completion behavior.
     */
    LEVEL_COMPLETE,
    /**
     * Transition that opens the shop state.
     */
    SHOP
}
