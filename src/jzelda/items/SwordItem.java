package jzelda.items;

import jzelda.model.GameModel;

/**
 * Collectible sword item. It permanently equips the player with a sword for the
 * current run, enabling the sword attack animation, longer reach and higher
 * damage.
 */
public class SwordItem extends Item {
    /** Creates a sword pickup item. */
    public SwordItem() {
        super("sword", "Spada", "Sblocca l'attacco con spada e aumenta il danno", 0, "item_sword");
    }

    @Override
    public void apply(GameModel model) {
        if (!model.getPlayer().hasSwordEquipped()) {
            model.getPlayer().equipSword();
            model.getPlayer().addInventory("sword", 1);
            model.addScore(150);
            model.addEffect("spada equipaggiata", model.getPlayer().getX(), model.getPlayer().getY());
            model.getFacade().audio().playEffect("purchase.wav");
            model.setMessage("Hai trovato la spada: attacco potenziato.");
        } else {
            model.addEffect("spada gia' equipaggiata", model.getPlayer().getX(), model.getPlayer().getY());
            model.setMessage("La spada e' gia' equipaggiata.");
        }
    }
}