package jzelda.items;

import jzelda.model.GameModel;

/**
 * Restores health when collected or bought.
 */
public class HealingItem extends Item {
    /** Creates a healing item. */
    public HealingItem() {
        super("healing", "Cuore curativo", "Ripristina 2 punti salute", 5, "item_heal");
    }

    @Override
    public void apply(GameModel model) {
        model.getPlayer().heal(2);
        model.addEffect("+salute", model.getPlayer().getX(), model.getPlayer().getY());
        model.getFacade().audio().playEffect("hit.wav");
    }
}
