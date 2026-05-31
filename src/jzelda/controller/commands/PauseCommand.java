package jzelda.controller.commands;

import jzelda.model.GameModel;

/** Command that toggles pause. */
public class PauseCommand implements InputCommand {
    @Override
    public void execute(GameModel model) {
        model.pause();
    }

    @Override
    public String getName() {
        return "PAUSE";
    }
}
