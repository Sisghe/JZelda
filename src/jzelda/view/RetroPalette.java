package jzelda.view;

import java.awt.Color;

/**
 * Original color palette inspired by retro hardware limitations without copying
 * protected game assets.
 */
public final class RetroPalette {
    /** Background ink. */
    public static final Color INK = new Color(18, 18, 28);
    /** HUD background. */
    public static final Color HUD = new Color(38, 43, 55);
    /** Floor color. */
    public static final Color FLOOR = new Color(86, 126, 83);
    /** Wall color. */
    public static final Color WALL = new Color(54, 68, 89);
    /** Accent yellow. */
    public static final Color GOLD = new Color(238, 196, 66);
    /** Text color. */
    public static final Color TEXT = new Color(238, 232, 208);
    /** Damage/heart color. */
    public static final Color RED = new Color(211, 71, 74);
    /** Rupee color. */
    public static final Color GREEN = new Color(78, 190, 110);
    /** Blue accent. */
    public static final Color BLUE = new Color(85, 159, 205);
    /** Shadow color. */
    public static final Color SHADOW = new Color(0, 0, 0, 120);

    private RetroPalette() {
        // Constants only.
    }
}
