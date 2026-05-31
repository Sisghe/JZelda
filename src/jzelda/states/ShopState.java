package jzelda.states;

import java.util.stream.Collectors;

import jzelda.controller.commands.BackCommand;
import jzelda.controller.commands.ConfirmCommand;
import jzelda.controller.commands.InputCommand;
import jzelda.controller.commands.NumberCommand;
import jzelda.model.GameModel;

/** Separate shop state where rupees can be spent on useful items. */
public class ShopState implements GameState {
    @Override
    public void enter(GameModel model) {
        model.setMenuOptions(model.getShop().getOffers().stream()
                .map(offer -> offer.getLabel() + " - " + offer.getPrice() + " rupie").collect(Collectors.toList()));
        model.setMessage("Bottega: premi 1-4 per acquistare, Invio/Esc per uscire.");
    }

    @Override
    public void update(GameModel model, long deltaMs) {
        // Shop is static except for rendering effects.
    }

    @Override
    public void handleCommand(GameModel model, InputCommand command) {
        if (command instanceof NumberCommand) {
            command.execute(model);
        } else if (command instanceof ConfirmCommand || command instanceof BackCommand) {
            model.leaveShop();
        }
    }

    @Override
    public String getName() {
        return "SHOP";
    }
}
