package jzelda.controller.commands;

import jzelda.model.GameModel;

/** Command that performs context-sensitive interaction. */
public class InteractCommand implements InputCommand {
    @Override
    public void execute(GameModel model) {
        model.interact();
    }

    @Override
    public String getName() {
        return "INTERACT";
    }
}
