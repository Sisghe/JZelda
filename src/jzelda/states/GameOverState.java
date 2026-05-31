package jzelda.states;

import jzelda.controller.commands.BackCommand;
import jzelda.controller.commands.ContinueCommand;
import jzelda.controller.commands.InputCommand;
import jzelda.model.GameModel;

/** State shown after player defeat, with continue support. */
public class GameOverState implements GameState {
    @Override
    public void enter(GameModel model) {
        model.setMessage("Game Over - premi C per continuare o Esc per menu.");
    }

    @Override
    public void update(GameModel model, long deltaMs) {
        model.tickTitleAnimation(deltaMs);
    }

    @Override
    public void handleCommand(GameModel model, InputCommand command) {
        if (command instanceof ContinueCommand) {
            command.execute(model);
        } else if (command instanceof BackCommand) {
            model.finishRun(false);
            model.changeState("MENU");
        }
    }

    @Override
    public String getName() {
        return "GAME_OVER";
    }
}
