package jzelda.controller.commands;

import jzelda.model.GameModel;

/**
 * Command pattern abstraction for keyboard actions.
 */
public interface InputCommand {
    /**
     * Executes the command against the model.
     *
     * @param model model to update
     */
    void execute(GameModel model);

    /**
     * @return human-readable command name
     */
    String getName();
}
