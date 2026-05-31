package jzelda.shop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jzelda.items.HealingItem;
import jzelda.items.LifeUpItem;
import jzelda.items.ShieldItem;
import jzelda.items.SpeedBoostItem;
import jzelda.model.GameModel;

/**
 * Shop model separated from the eight playable levels. It sells useful items in
 * exchange for rupees.
 */
public class Shop {
    private final List<ShopOffer> offers = new ArrayList<>();

    /** Creates the default shop inventory. */
    public Shop() {
        offers.add(new ShopOffer("1 - Cuore curativo", 5, HealingItem::new));
        offers.add(new ShopOffer("2 - Scudo di legno", 12, ShieldItem::new));
        offers.add(new ShopOffer("3 - Stivali leggeri", 15, SpeedBoostItem::new));
        offers.add(new ShopOffer("4 - Frammento vitale", 20, LifeUpItem::new));
    }

    /**
     * @return immutable list of offers
     */
    public List<ShopOffer> getOffers() {
        return Collections.unmodifiableList(offers);
    }

    /**
     * Returns offers the player can afford using a stream filter and sort.
     *
     * @param rupees available rupees
     * @return affordable offers sorted by price
     */
    public List<ShopOffer> affordableOffers(int rupees) {
        return offers.stream().filter(offer -> offer.getPrice() <= rupees).sorted(Comparator.comparingInt(ShopOffer::getPrice))
                .collect(Collectors.toList());
    }

    /**
     * Buys an item by one-based menu number.
     *
     * @param model game model to update
     * @param number one-based offer number
     * @return purchased offer if successful
     */
    public Optional<ShopOffer> purchase(GameModel model, int number) {
        int index = number - 1;
        if (index < 0 || index >= offers.size()) {
            return Optional.empty();
        }
        ShopOffer offer = offers.get(index);
        if (model.getPlayer().spendRupees(offer.getPrice())) {
            offer.createItem().apply(model);
            model.addScore(offer.getPrice() * 2);
            model.getFacade().audio().playEffect("purchase.wav");
            return Optional.of(offer);
        }
        return Optional.empty();
    }
}
