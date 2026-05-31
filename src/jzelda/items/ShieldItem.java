package jzelda.items;

import jzelda.model.GameModel;

/**
 * Activates a timed shield that halves incoming damage.
 */
public class ShieldItem extends Item {
    /** Creates a shield item. */
    public ShieldItem() {
        super("shield", "Scudo di legno", "Riduce i danni per 12 secondi", 12, "item_shield");
    }

    @Override
    public void apply(GameModel model) {
        model.getPlayer().activateShield(12_000);
        model.getPlayer().addInventory("shield", 1);
        model.addEffect("scudo", model.getPlayer().getX(), model.getPlayer().getY());
        model.getFacade().audio().playEffect("purchase.wav");
    }
}
