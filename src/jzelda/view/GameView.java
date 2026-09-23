package jzelda.view;

import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.WindowConstants;
import jzelda.model.GameModel;
import jzelda.patterns.ModelEvent;
import jzelda.patterns.ModelObserver;

/**
 * Swing frame in the MVC View role. It observes the model and repaints the game
 * panel when state changes, but it does not implement gameplay rules.
 */
public class GameView extends JFrame implements ModelObserver {
    private static final long serialVersionUID = 1L;
    private final GamePanel panel;

    /**
     * Creates the main game window.
     *
     * @param model model to display
     */
    public GameView(GameModel model) {
    super("JZelda - Retro Adventure");

    this.panel = new GamePanel(model);

    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setLayout(new BorderLayout());

    setResizable(true);

    add(panel, BorderLayout.CENTER);
    pack();

    setMinimumSize(getSize());
    setLocationRelativeTo(null);
}

    @Override
    public void onModelChanged(ModelEvent event) {
        panel.repaint();
    }

    /**
     * Requests keyboard focus for the game panel.
     */
    public void focusGame() {
        panel.requestFocusInWindow();
    }

    /**
     * Adds a controller as key listener to the game panel.
     *
     * @param listener controller key listener
     */
    public void addGameKeyListener(java.awt.event.KeyListener listener) {
        panel.addKeyListener(listener);
    }

    /**
     * Prompts the user for a nickname when creating a profile.
     *
     * @return nickname or {@code null} when cancelled
     */
    public String promptForProfileNickname() {
        return JOptionPane.showInputDialog(this, "Inserisci nickname profilo:", "Nuovo profilo",
                JOptionPane.PLAIN_MESSAGE);
    }
}
