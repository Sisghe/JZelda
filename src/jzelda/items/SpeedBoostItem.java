package jzelda.items;

import jzelda.model.GameModel;

/**
 * Temporarily increases movement speed.
 */
public class SpeedBoostItem extends Item {
    /** Creates a speed boost item. */
    public SpeedBoostItem() {
        super("speed", "Stivali leggeri", "Aumenta la velocità per 10 secondi", 15, "item_speed");
    }

    @Override
    public void apply(GameModel model) {
        model.getPlayer().activateSpeedBoost(10_000);
        model.getPlayer().addInventory("speed", 1);
        model.addEffect("veloce", model.getPlayer().getX(), model.getPlayer().getY());
        model.getFacade().audio().playEffect("purchase.wav");
    }
}
