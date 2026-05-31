package jzelda.states;

import jzelda.controller.commands.BackCommand;
import jzelda.controller.commands.ConfirmCommand;
import jzelda.controller.commands.InputCommand;
import jzelda.model.GameModel;

/** Final victory state shown after all eight levels are cleared. */
public class VictoryState implements GameState {
    @Override
    public void enter(GameModel model) {
        model.setMessage("Vittoria! Hai liberato il regno dell'alba. Invio per menu.");
    }

    @Override
    public void update(GameModel model, long deltaMs) {
        model.tickTitleAnimation(deltaMs);
    }

    @Override
    public void handleCommand(GameModel model, InputCommand command) {
        if (command instanceof ConfirmCommand || command instanceof BackCommand) {
            model.changeState("MENU");
        }
    }

    @Override
    public String getName() {
        return "VICTORY";
    }
}
