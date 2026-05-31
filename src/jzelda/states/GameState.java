package jzelda.states;

import jzelda.controller.commands.InputCommand;
import jzelda.model.GameModel;

/**
 * State pattern interface for screens and gameplay phases.
 */
public interface GameState {
    /**
     * Called when this state becomes active.
     *
     * @param model active model
     */
    void enter(GameModel model);

    /**
     * Updates state-specific logic.
     *
     * @param model active model
     * @param deltaMs elapsed milliseconds
     */
    void update(GameModel model, long deltaMs);

    /**
     * Handles a keyboard command in this state.
     *
     * @param model active model
     * @param command command to handle
     */
    void handleCommand(GameModel model, InputCommand command);

    /**
     * @return display state name
     */
    String getName();
}
