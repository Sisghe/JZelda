package jzelda.states;

import jzelda.controller.commands.BackCommand;
import jzelda.controller.commands.InputCommand;
import jzelda.model.GameModel;

/** Non-blocking door opening animation state. */
public final class DoorOpeningState implements GameState {
    @Override
    public void enter(GameModel model) {
        model.setMessage("La porta si sta aprendo...");
    }

    @Override
    public void update(GameModel model, long deltaMs) {
        model.updateDoorOpening(deltaMs);
    }

    @Override
    public void handleCommand(GameModel model, InputCommand command) {
        if (command instanceof BackCommand) {
            // Opening cannot be cancelled after the key has been consumed.
        }
    }

    @Override
    public String getName() {
        return "DOOR_OPENING";
    }
}
