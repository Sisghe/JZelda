package jzelda.states;

import java.util.Arrays;
import jzelda.controller.commands.BackCommand;
import jzelda.controller.commands.ConfirmCommand;
import jzelda.controller.commands.InputCommand;
import jzelda.controller.commands.NavigateMenuCommand;
import jzelda.model.GameModel;

/** Confirmation menu shown before consuming a key for a locked door. */
public final class DoorPromptState implements GameState {
    @Override
    public void enter(GameModel model) {
        model.setMenuOptions(Arrays.asList("Usa chiave", "Annulla"));
        model.setMessage("Vuoi usare una chiave?");
    }

    @Override
    public void update(GameModel model, long deltaMs) {
        // Prompt state is intentionally static while awaiting input.
    }

    @Override
    public void handleCommand(GameModel model, InputCommand command) {
        if (command instanceof NavigateMenuCommand) {
            command.execute(model);
        } else if (command instanceof ConfirmCommand) {
            model.confirmDoorPrompt();
        } else if (command instanceof BackCommand) {
            model.cancelDoorPrompt();
        }
    }

    @Override
    public String getName() {
        return "DOOR_PROMPT";
    }
}
