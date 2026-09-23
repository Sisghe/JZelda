package jzelda.model;

import java.awt.geom.Rectangle2D;

/**
 * Centralized immutable configuration for tile sizes, viewport dimensions and
 * timing constants used by model, view and controller.
 */
public final class GameConfig {
    /** Size in pixels of a single map tile. */
    public static final int TILE_SIZE = 32;
    /** Logical tile columns for map collision and positioning. */
    public static final int MAP_COLUMNS = 16;
    /** Logical tile rows for map collision and positioning. */
    public static final int MAP_ROWS = 11;
    /** Logical room width in pixels (16 x 32). */
    public static final int ROOM_WIDTH = MAP_COLUMNS * TILE_SIZE;
    /** Logical room height in pixels (11 x 32). */
    public static final int ROOM_HEIGHT = MAP_ROWS * TILE_SIZE;
    /** Pixel height of the top HUD. */
    public static final int HUD_HEIGHT = 96;
    /** Play area width in the full UI frame (legacy UI area). */
    public static final int PLAY_WIDTH = 640;
    /** Play area height in the full UI frame (legacy UI area). */
    public static final int PLAY_HEIGHT = 448;
    /** X offset of the room inside the play area. */
    public static final int ROOM_OFFSET_X = (PLAY_WIDTH - ROOM_WIDTH) / 2;
    /** Y offset of the room inside the play area. */
    public static final int ROOM_OFFSET_Y = (PLAY_HEIGHT - ROOM_HEIGHT) / 2;
    /** Full panel width in pixels. */
    public static final int WINDOW_WIDTH = PLAY_WIDTH;
    /** Full panel height in pixels. */
    public static final int WINDOW_HEIGHT = HUD_HEIGHT + PLAY_HEIGHT;
    /** Room right bound in absolute play coordinates. */
    public static final int ROOM_MAX_X = ROOM_OFFSET_X + ROOM_WIDTH;
    /** Room bottom bound in absolute play coordinates. */
    public static final int ROOM_MAX_Y = ROOM_OFFSET_Y + ROOM_HEIGHT;
    /** Swing timer delay, approximately 60 frames per second. */
    public static final int FRAME_DELAY_MS = 16;
    /** Initial number of character lives in a new run. */
    public static final int STARTING_LIVES = 3;
    /** Initial number of continues for a profile run. */
    public static final int STARTING_CONTINUES = 2;

    private GameConfig() {
        // Constants only.
    }

    /**
     * Returns the top-left pixel of a logical tile in play coordinates.
     *
     * @param tileColumn tile column
     * @return X coordinate in play coordinates
     */
    public static int toPlayX(int tileColumn) {
        return ROOM_OFFSET_X + tileColumn * TILE_SIZE;
    }

    /**
     * Returns the top-left pixel of a logical tile in play coordinates.
     *
     * @param tileRow tile row
     * @return Y coordinate in play coordinates
     */
    public static int toPlayY(int tileRow) {
        return ROOM_OFFSET_Y + tileRow * TILE_SIZE;
    }

    /**
     * Converts play X coordinates to tile columns, including room offsets.
     *
     * @param playX play coordinate
     * @return tile column (can be negative/outside bounds)
     */
    public static int toTileColumn(double playX) {
        return (int) Math.floor((playX - ROOM_OFFSET_X) / TILE_SIZE);
    }

    /**
     * Converts play Y coordinates to tile rows, including room offsets.
     *
     * @param playY play coordinate
     * @return tile row (can be negative/outside bounds)
     */
    public static int toTileRow(double playY) {
        return (int) Math.floor((playY - ROOM_OFFSET_Y) / TILE_SIZE);
    }

    /**
     * Checks whether the rectangle lies entirely inside the logical room bounds.
     *
     * @param rectangle rectangle in play coordinates
     * @return true when fully inside the room
     */
    public static boolean isInsideRoom(Rectangle2D rectangle) {
        return rectangle.getMinX() >= ROOM_OFFSET_X && rectangle.getMinY() >= ROOM_OFFSET_Y
                && rectangle.getMaxX() <= ROOM_MAX_X && rectangle.getMaxY() <= ROOM_MAX_Y;
    }

    /**
     * Returns the screen offset for play coordinates.
     *
     * @return play area Y offset used to render below HUD
     */
    public static int hudOffsetY() {
        return HUD_HEIGHT;
    }
}
