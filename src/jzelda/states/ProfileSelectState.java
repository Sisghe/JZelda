package jzelda.states;

import java.util.List;
import java.util.stream.Collectors;

import jzelda.controller.commands.BackCommand;
import jzelda.controller.commands.ConfirmCommand;
import jzelda.controller.commands.InputCommand;
import jzelda.controller.commands.NavigateMenuCommand;
import jzelda.model.GameModel;
import jzelda.persistence.Profile;

/** State for profile selection and statistics display. */
public class ProfileSelectState implements GameState {
    @Override
    public void enter(GameModel model) {
        List<String> options = model.getFacade().profiles().getProfiles().stream()
                .map(profile -> profile.getNickname() + "  W:" + profile.getGamesWon() + " L:" + profile.getGamesLost()
                        + " Best:" + profile.getBestScore())
                .collect(Collectors.toList());
        if (options.isEmpty()) {
            options.add("Nessun profilo - premi N per crearne uno");
        }
        model.setMenuOptions(options);
        model.setMessage("Seleziona profilo. Premi N per creare un nuovo nickname.");
    }

    @Override
    public void update(GameModel model, long deltaMs) {
        // Static menu.
    }

    @Override
    public void handleCommand(GameModel model, InputCommand command) {
        if (command instanceof NavigateMenuCommand) {
            command.execute(model);
        } else if (command instanceof ConfirmCommand) {
            Profile selected = model.getFacade().profiles().selectByIndex(model.getMenuIndex());
            model.startNewGame(selected);
        } else if (command instanceof BackCommand) {
            model.changeState("MENU");
        }
    }

    @Override
    public String getName() {
        return "PROFILE";
    }
}
