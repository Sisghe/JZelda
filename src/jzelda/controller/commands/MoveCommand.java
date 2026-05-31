package jzelda.controller.commands;

import jzelda.entities.Direction;
import jzelda.model.GameModel;

/** Command that moves the player in a cardinal direction. */
public class MoveCommand implements InputCommand {
    private final Direction direction;

    /**
     * @param direction movement direction
     */
    public MoveCommand(Direction direction) {
        this.direction = direction;
    }

    /**
     * @return movement direction
     */
    public Direction getDirection() {
        return direction;
    }

    @Override
    public void execute(GameModel model) {
        model.movePlayer(direction);
    }

    @Override
    public String getName() {
        return "MOVE_" + direction;
    }
}
