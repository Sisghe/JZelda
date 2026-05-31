package jzelda.controller.commands;

import jzelda.model.GameModel;

/** Command that confirms the current menu selection. */
public class ConfirmCommand implements InputCommand {
    @Override
    public void execute(GameModel model) {
        model.confirmSelection();
    }

    @Override
    public String getName() {
        return "CONFIRM";
    }
}
