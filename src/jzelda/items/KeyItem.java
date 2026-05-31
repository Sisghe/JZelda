package jzelda.items;

import jzelda.model.GameModel;

/**
 * Adds a key to the inventory. Keys can be required by exits or doors.
 */
public class KeyItem extends Item {
    /** Creates a key item. */
    public KeyItem() {
        super("key", "Chiave antica", "Apre passaggi sigillati", 8, "item_key");
    }

    @Override
    public void apply(GameModel model) {
        model.getPlayer().addInventory("key", 1);
        model.addScore(25);
        model.addEffect("chiave", model.getPlayer().getX(), model.getPlayer().getY());
        model.getFacade().audio().playEffect("rupee.wav");
    }
}
