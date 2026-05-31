package jzelda.items;

import jzelda.model.GameModel;

/**
 * Base type for usable or collectible items. Concrete items apply their effects
 * directly to the model while keeping their data immutable.
 */
public abstract class Item {
    private final String id;
    private final String name;
    private final String description;
    private final int shopPrice;
    private final String spriteKey;

    /**
     * Creates an item definition.
     *
     * @param id stable identifier used in inventory and persistence
     * @param name display name
     * @param description short gameplay effect description
     * @param shopPrice price in rupees, {@code 0} for field pickups
     * @param spriteKey logical sprite key
     */
    protected Item(String id, String name, String description, int shopPrice, String spriteKey) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.shopPrice = shopPrice;
        this.spriteKey = spriteKey;
    }

    /**
     * Applies the item effect to the game.
     *
     * @param model game model to modify
     */
    public abstract void apply(GameModel model);

    /**
     * @return stable item identifier
     */
    public String getId() {
        return id;
    }

    /**
     * @return display name
     */
    public String getName() {
        return name;
    }

    /**
     * @return effect description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return shop price in rupees
     */
    public int getShopPrice() {
        return shopPrice;
    }

    /**
     * @return sprite key used by the view
     */
    public String getSpriteKey() {
        return spriteKey;
    }
}
