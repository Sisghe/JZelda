package jzelda.controller.commands;

import jzelda.model.GameModel;

/** Command that selects a numbered option, mainly used in the shop. */
public class NumberCommand implements InputCommand {
    private final int number;

    /**
     * @param number selected number
     */
    public NumberCommand(int number) {
        this.number = number;
    }

    /**
     * @return selected number
     */
    public int getNumber() {
        return number;
    }

    @Override
    public void execute(GameModel model) {
        model.selectNumber(number);
    }

    @Override
    public String getName() {
        return "NUMBER_" + number;
    }
}
