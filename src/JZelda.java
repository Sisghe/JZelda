import javax.swing.SwingUtilities;

import jzelda.app.GameLauncher;

/**
 * Entry point of the JZelda project.
 * <p>
 * The class intentionally remains in the default package so that it can be
 * launched easily from Eclipse by selecting {@code JZelda.main}. The rest of
 * the application is organized in named packages under {@code jzelda.*}.
 */
public final class JZelda {

    private JZelda() {
        // Utility class: no instances.
    }

    /**
     * Starts the Swing application on the Event Dispatch Thread.
     *
     * @param args command line arguments, currently ignored
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameLauncher().start());
    }
}
