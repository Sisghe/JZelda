package jzelda.controller.commands;

import jzelda.model.GameModel;

/** Command that moves a menu cursor. */
public class NavigateMenuCommand implements InputCommand {
    private final int delta;

    /**
     * @param delta signed movement of the menu cursor
     */
    public NavigateMenuCommand(int delta) {
        this.delta = delta;
    }

    /**
     * @return cursor delta
     */
    public int getDelta() {
        return delta;
    }

    @Override
    public void execute(GameModel model) {
        model.navigateMenu(delta);
    }

    @Override
    public String getName() {
        return "NAVIGATE_" + delta;
    }
}
