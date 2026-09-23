package jzelda.states;

import jzelda.controller.commands.AttackCommand;
import jzelda.controller.commands.BackCommand;
import jzelda.controller.commands.InputCommand;
import jzelda.controller.commands.InteractCommand;
import jzelda.controller.commands.MoveCommand;
import jzelda.controller.commands.PauseCommand;
import jzelda.model.GameModel;

/** Active gameplay state. */
public class PlayingState implements GameState {
    @Override
    public void enter(GameModel model) {
        model.setMessage("Esplora, raccogli rupie e sconfiggi i guardiani.");
    }

    @Override
    public void update(GameModel model, long deltaMs) {
        model.updateGameplay(deltaMs);
    }

    @Override
    public void handleCommand(GameModel model, InputCommand command) {
        if (command instanceof MoveCommand || command instanceof AttackCommand || command instanceof InteractCommand) {
            command.execute(model);
        } else if (command instanceof PauseCommand || command instanceof BackCommand) {
            model.changeState("PAUSED");
        } else {
            // Execute any other input commands (e.g. anonymous toggle-minimap command)
            if (command != null) {
                command.execute(model);
            }
        }
    }

    @Override
    public String getName() {
        return "PLAYING";
    }
}
