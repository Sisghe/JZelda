package jzelda.controller;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.Set;

import javax.swing.Timer;

import jzelda.controller.commands.AttackCommand;
import jzelda.controller.commands.BackCommand;
import jzelda.controller.commands.ConfirmCommand;
import jzelda.controller.commands.ContinueCommand;
import jzelda.controller.commands.InputCommand;
import jzelda.controller.commands.InteractCommand;
import jzelda.controller.commands.MoveCommand;
import jzelda.controller.commands.NavigateMenuCommand;
import jzelda.controller.commands.NumberCommand;
import jzelda.controller.commands.PauseCommand;
import jzelda.entities.Direction;
import jzelda.model.GameConfig;
import jzelda.model.GameModel;
import jzelda.view.GameView;

/**
 * MVC controller. It translates keyboard input into Command objects, runs the
 * Swing timer game loop and coordinates small UI interactions such as profile
 * creation prompts.
 */
public class GameController implements KeyListener, ActionListener {
    private final GameModel model;
    private final GameView view;
    private final Set<Integer> pressedKeys = new HashSet<>();
    private final Timer timer;
    private long lastTick;

    /**
     * Creates a controller.
     *
     * @param model model to update
     * @param view view to coordinate
     */
    public GameController(GameModel model, GameView view) {
        this.model = model;
        this.view = view;
        this.timer = new Timer(GameConfig.FRAME_DELAY_MS, this);
    }

    /** Starts the Swing game loop. */
    public void start() {
        lastTick = System.currentTimeMillis();
        timer.start();
    }

    /** Stops the Swing game loop. */
    public void stop() {
        timer.stop();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        long now = System.currentTimeMillis();
        long delta = Math.max(1, now - lastTick);
        lastTick = now;
        issueMovementCommand();
        model.update(delta);
        if (model.isExitRequested()) {
            timer.stop();
            view.dispose();
        }
    }

    private void issueMovementCommand() {
        if (!"PLAYING".equals(model.getStateName())) {
            return;
        }
        Direction direction = Direction.NONE;
        if (pressedKeys.contains(KeyEvent.VK_LEFT) || pressedKeys.contains(KeyEvent.VK_A)) {
            direction = Direction.LEFT;
        } else if (pressedKeys.contains(KeyEvent.VK_RIGHT) || pressedKeys.contains(KeyEvent.VK_D)) {
            direction = Direction.RIGHT;
        } else if (pressedKeys.contains(KeyEvent.VK_UP) || pressedKeys.contains(KeyEvent.VK_W)) {
            direction = Direction.UP;
        } else if (pressedKeys.contains(KeyEvent.VK_DOWN) || pressedKeys.contains(KeyEvent.VK_S)) {
            direction = Direction.DOWN;
        }
        if (direction != Direction.NONE) {
            model.handleCommand(new MoveCommand(direction));
        }
    }

    @Override
    public void keyPressed(KeyEvent event) {
        int code = event.getKeyCode();
        pressedKeys.add(code);
        if (code == KeyEvent.VK_N && "PROFILE".equals(model.getStateName())) {
            String nickname = view.promptForProfileNickname();
            if (nickname != null) {
                model.createProfile(nickname);
            }
            return;
        }
        InputCommand command = commandForKey(code);
        if (command != null) {
            model.handleCommand(command);
        }
    }

    private InputCommand commandForKey(int code) {
        boolean playing = "PLAYING".equals(model.getStateName());
        if (!playing && (code == KeyEvent.VK_UP || code == KeyEvent.VK_W)) {
            return new NavigateMenuCommand(-1);
        }
        if (!playing && (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S)) {
            return new NavigateMenuCommand(1);
        }
        if (code == KeyEvent.VK_SPACE) {
            return new AttackCommand();
        }
        if (code == KeyEvent.VK_E) {
            return new InteractCommand();
        }
        if (code == KeyEvent.VK_ENTER) {
            return new ConfirmCommand();
        }
        if (code == KeyEvent.VK_P) {
            return new PauseCommand();
        }
        if (code == KeyEvent.VK_ESCAPE) {
            return new BackCommand();
        }
        if (code == KeyEvent.VK_C) {
            return new ContinueCommand();
        }
        if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_9) {
            return new NumberCommand(code - KeyEvent.VK_0);
        }
        return null;
    }

    @Override
    public void keyReleased(KeyEvent event) {
        pressedKeys.remove(event.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent event) {
        // Not used; keyPressed gives stable key codes for commands.
    }
}
