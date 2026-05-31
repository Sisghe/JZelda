package jzelda.controller.commands;

import jzelda.model.GameModel;

/** Command that continues a run from the game-over screen. */
public class ContinueCommand implements InputCommand {
    @Override
    public void execute(GameModel model) {
        model.continueRun();
    }

    @Override
    public String getName() {
        return "CONTINUE";
    }
}
