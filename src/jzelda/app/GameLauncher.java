package jzelda.app;

import jzelda.audio.AudioManager;
import jzelda.controller.GameController;
import jzelda.model.GameModel;
import jzelda.patterns.GameFacade;
import jzelda.persistence.FileLeaderboardRepository;
import jzelda.persistence.FileProfileRepository;
import jzelda.persistence.Leaderboard;
import jzelda.persistence.ProfileManager;
import jzelda.resources.ResourceManager;
import jzelda.view.GameView;

/**
 * Composition root for the application. It wires model, view, controller and
 * infrastructure without putting construction logic in the entry-point class.
 */
public class GameLauncher {
    /**
     * Creates all subsystems and shows the game window.
     */
    public void start() {
        ResourceManager resources = ResourceManager.getInstance();
        AudioManager audio = AudioManager.getInstance();
        ProfileManager profileManager = new ProfileManager(new FileProfileRepository());
        Leaderboard leaderboard = new Leaderboard(new FileLeaderboardRepository());
        GameFacade facade = new GameFacade(resources, audio, profileManager, leaderboard);
        GameModel model = new GameModel(facade);
        GameView view = new GameView(model);
        GameController controller = new GameController(model, view);
        model.addObserver(view);
        view.addGameKeyListener(controller);
        view.setVisible(true);
        view.focusGame();
        controller.start();
    }
}
