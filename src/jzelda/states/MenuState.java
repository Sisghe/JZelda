package jzelda.states;

import java.util.Arrays;

import jzelda.controller.commands.ConfirmCommand;
import jzelda.controller.commands.InputCommand;
import jzelda.controller.commands.NavigateMenuCommand;
import jzelda.model.GameModel;

/** Main title/menu state. */
public class MenuState implements GameState {
    @Override
    public void enter(GameModel model) {
        model.setMenuOptions(Arrays.asList("Nuova partita / profilo", "Classifica", "Esci"));
        model.setMessage("JZelda - avventura retrò originale");
    }

    @Override
    public void update(GameModel model, long deltaMs) {
        model.tickTitleAnimation(deltaMs);
    }

    @Override
    public void handleCommand(GameModel model, InputCommand command) {
        if (command instanceof NavigateMenuCommand) {
            command.execute(model);
        } else if (command instanceof ConfirmCommand) {
            int index = model.getMenuIndex();
            if (index == 0) {
                model.changeState("PROFILE");
            } else if (index == 1) {
                model.changeState("LEADERBOARD");
            } else {
                model.requestExit();
            }
        }
    }

    @Override
    public String getName() {
        return "MENU";
    }
}
