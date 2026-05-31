package jzelda.model;

/**
 * Centralized immutable configuration for tile sizes, viewport dimensions and
 * timing constants used by model, view and controller.
 */
public final class GameConfig {
    /** Size in pixels of a single map tile. */
    public static final int TILE_SIZE = 32;
    /** Number of tile columns in every level map. */
    public static final int MAP_COLUMNS = 20;
    /** Number of tile rows in every level map. */
    public static final int MAP_ROWS = 14;
    /** Pixel width of the play area. */
    public static final int PLAY_WIDTH = MAP_COLUMNS * TILE_SIZE;
    /** Pixel height of the play area. */
    public static final int PLAY_HEIGHT = MAP_ROWS * TILE_SIZE;
    /** Pixel height of the top HUD. */
    public static final int HUD_HEIGHT = 96;
    /** Full panel width in pixels. */
    public static final int WINDOW_WIDTH = PLAY_WIDTH;
    /** Full panel height in pixels. */
    public static final int WINDOW_HEIGHT = HUD_HEIGHT + PLAY_HEIGHT;
    /** Swing timer delay, approximately 60 frames per second. */
    public static final int FRAME_DELAY_MS = 16;
    /** Initial number of character lives in a new run. */
    public static final int STARTING_LIVES = 3;
    /** Initial number of continues for a profile run. */
    public static final int STARTING_CONTINUES = 2;

    private GameConfig() {
        // Constants only.
    }
}
