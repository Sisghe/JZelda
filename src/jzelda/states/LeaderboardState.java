package jzelda.states;

import jzelda.controller.commands.BackCommand;
import jzelda.controller.commands.ConfirmCommand;
import jzelda.controller.commands.InputCommand;
import jzelda.model.GameModel;

/** Leaderboard display state. */
public class LeaderboardState implements GameState {
    @Override
    public void enter(GameModel model) {
        model.setMessage("Classifica - Invio/Esc per tornare al menu.");
    }

    @Override
    public void update(GameModel model, long deltaMs) {
        // Static view.
    }

    @Override
    public void handleCommand(GameModel model, InputCommand command) {
        if (command instanceof BackCommand || command instanceof ConfirmCommand) {
            model.changeState("MENU");
        }
    }

    @Override
    public String getName() {
        return "LEADERBOARD";
    }
}
