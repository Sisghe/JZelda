package jzelda.controller.commands;

import jzelda.model.GameModel;

/** Command that starts a player attack. */
public class AttackCommand implements InputCommand {
    @Override
    public void execute(GameModel model) {
        model.attack();
    }

    @Override
    public String getName() {
        return "ATTACK";
    }
}
