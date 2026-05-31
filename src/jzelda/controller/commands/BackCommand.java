package jzelda.controller.commands;

import jzelda.model.GameModel;

/** Command that returns to the previous menu or exits pause screens. */
public class BackCommand implements InputCommand {
    @Override
    public void execute(GameModel model) {
        model.goBack();
    }

    @Override
    public String getName() {
        return "BACK";
    }
}
