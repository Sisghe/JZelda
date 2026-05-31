package jzelda.items;

import jzelda.model.GameModel;

/**
 * Permanently increases maximum health for the current run.
 */
public class LifeUpItem extends Item {
    /** Creates a life-up item. */
    public LifeUpItem() {
        super("life_up", "Frammento vitale", "Aumenta la salute massima", 20, "item_life");
    }

    @Override
    public void apply(GameModel model) {
        model.getPlayer().increaseMaxHealth(2);
        model.addScore(100);
        model.addEffect("vita+", model.getPlayer().getX(), model.getPlayer().getY());
        model.getFacade().audio().playEffect("levelcomplete.wav");
    }
}
