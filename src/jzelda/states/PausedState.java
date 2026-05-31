package jzelda.states;

import java.util.Arrays;

import jzelda.controller.commands.BackCommand;
import jzelda.controller.commands.ConfirmCommand;
import jzelda.controller.commands.InputCommand;
import jzelda.controller.commands.NavigateMenuCommand;
import jzelda.controller.commands.PauseCommand;
import jzelda.model.GameModel;

/** Pause and secondary/inventory screen state. */
public class PausedState implements GameState {
    @Override
    public void enter(GameModel model) {
        model.setMenuOptions(Arrays.asList("Riprendi", "Vai alla bottega", "Classifica", "Salva e torna al menu"));
        model.setMessage("Pausa - inventario e statistiche visibili a schermo.");
    }

    @Override
    public void update(GameModel model, long deltaMs) {
        // Paused gameplay.
    }

    @Override
    public void handleCommand(GameModel model, InputCommand command) {
        if (command instanceof NavigateMenuCommand) {
            command.execute(model);
        } else if (command instanceof PauseCommand || command instanceof BackCommand) {
            model.changeState("PLAYING");
        } else if (command instanceof ConfirmCommand) {
            int option = model.getMenuIndex();
            if (option == 0) {
                model.changeState("PLAYING");
            } else if (option == 1) {
                model.enterShop(false);
            } else if (option == 2) {
                model.changeState("LEADERBOARD");
            } else {
                model.getFacade().profiles().save();
                model.changeState("MENU");
            }
        }
    }

    @Override
    public String getName() {
        return "PAUSED";
    }
}
