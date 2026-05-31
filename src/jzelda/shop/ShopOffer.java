package jzelda.shop;

import java.util.function.Supplier;

import jzelda.items.Item;

/**
 * Immutable shop offer containing a supplier that creates a fresh item instance
 * when purchased.
 */
public class ShopOffer {
    private final String label;
    private final int price;
    private final Supplier<Item> itemSupplier;

    /**
     * Creates an offer.
     *
     * @param label display label
     * @param price price in rupees
     * @param itemSupplier creates the purchased item
     */
    public ShopOffer(String label, int price, Supplier<Item> itemSupplier) {
        this.label = label;
        this.price = price;
        this.itemSupplier = itemSupplier;
    }

    /**
     * @return display label
     */
    public String getLabel() {
        return label;
    }

    /**
     * @return price in rupees
     */
    public int getPrice() {
        return price;
    }

    /**
     * @return new item instance
     */
    public Item createItem() {
        return itemSupplier.get();
    }
}
