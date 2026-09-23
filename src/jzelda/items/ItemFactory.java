package jzelda.items;

/**
 * Factory Method creator for item instances used by maps and the shop.
 */
public class ItemFactory {
    /**
     * Creates an item from a symbolic identifier.
     *
     * @param code item code: {@code H}, {@code K}, {@code S}, {@code L},
     *             {@code B} or {@code T}
     * @return new item instance
     * @throws IllegalArgumentException when the code is unknown
     */
    public Item createItem(char code) {
        switch (code) {
        case 'H':
            return new HealingItem();
        case 'K':
            return new KeyItem();
        case 'S':
            return new ShieldItem();
        case 'L':
            return new LifeUpItem();
        case 'B':
            return new SpeedBoostItem();
        case 'T':
            return new SwordItem();
        default:
            throw new IllegalArgumentException("Codice item non supportato: " + code);
        }
    }

    /**
     * Creates an item from a stable id.
     *
     * @param id item id
     * @return new item instance
     */
    public Item createById(String id) {
        if ("healing".equals(id)) {
            return new HealingItem();
        }
        if ("key".equals(id)) {
            return new KeyItem();
        }
        if ("shield".equals(id)) {
            return new ShieldItem();
        }
        if ("life_up".equals(id)) {
            return new LifeUpItem();
        }
        if ("speed".equals(id)) {
            return new SpeedBoostItem();
        }
        if ("sword".equals(id)) {
            return new SwordItem();
        }
        throw new IllegalArgumentException("Id item non supportato: " + id);
    }
}
