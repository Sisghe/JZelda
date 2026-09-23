package jzelda.levels;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import jzelda.entities.Enemy;
import jzelda.entities.Projectile;
import jzelda.model.GameConfig;

/**
 * Model of a single playable level, including map collision, enemies, pickups,
 * projectiles and completion metadata.
 */
public class Level {
    private final int id;
    private final String name;
    private final TileType[][] tiles;
    private final List<Enemy> enemies;
    private final List<ItemDrop> itemDrops;
    private final List<RupeePickup> rupees;
    private final List<Projectile> projectiles = new ArrayList<>();
    private final double startX;
    private final double startY;
    private final BufferedImage backgroundImage;
    private final List<LevelExit> exits;
    private boolean completed;
    private final Rectangle2D.Double legacyExitBounds;
    private final boolean requiresLegacyKey;

    /**
     * Creates a level from parsed map data.
     *
     * @param id level number
     * @param name display name
     * @param tiles tile matrix
     * @param enemies enemies contained in the level
     * @param itemDrops item pickups contained in the level
     * @param rupees rupee pickups contained in the level
     * @param backgroundImage cached background image for this level
     * @param startX player start x coordinate
     * @param startY player start y coordinate
     * @param exitBounds exit collision rectangle
     * @param requiresKey whether a key is required to complete the level
     */
    public Level(int id, String name, TileType[][] tiles, List<Enemy> enemies, List<ItemDrop> itemDrops,
            List<RupeePickup> rupees, BufferedImage backgroundImage, double startX, double startY,
            List<LevelExit> exits, Rectangle2D.Double legacyExitBounds, boolean requiresLegacyKey) {
        this.id = id;
        this.name = name;
        this.tiles = tiles;
        this.enemies = new ArrayList<>(enemies);
        this.itemDrops = new ArrayList<>(itemDrops);
        this.rupees = new ArrayList<>(rupees);
        this.backgroundImage = backgroundImage;
        this.startX = startX;
        this.startY = startY;
        this.exits = new ArrayList<>(exits == null ? List.of() : exits);
        this.legacyExitBounds = legacyExitBounds;
        this.requiresLegacyKey = requiresLegacyKey;
    }

    /**
     * @return level id, starting from 1
     */
    public int getId() {
        return id;
    }

    /**
     * @return display name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets a tile at grid coordinates.
     *
     * @param col tile column
     * @param row tile row
     * @return tile type, wall outside bounds
     */
    public TileType getTile(int col, int row) {
        if (row < 0 || row >= tiles.length || col < 0 || col >= tiles[row].length) {
            return TileType.WALL;
        }
        return tiles[row][col];
    }

    /**
     * Checks collision with solid tiles.
     *
     * @param rectangle rectangle in world coordinates
     * @return {@code true} when any solid tile is touched
     */
    public boolean isBlocked(Rectangle2D rectangle) {
        if (!GameConfig.isInsideRoom(rectangle)) {
            return true;
        }
        int minCol = GameConfig.toTileColumn(rectangle.getMinX());
        int maxCol = GameConfig.toTileColumn(rectangle.getMaxX() - 1);
        int minRow = GameConfig.toTileRow(rectangle.getMinY());
        int maxRow = GameConfig.toTileRow(rectangle.getMaxY() - 1);
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                if (getTile(col, row).isBlocked()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Applies persistent door progress to this room's runtime tile matrix. */
    public void applyDungeonProgress(DungeonProgress progress) {
        if (progress == null) {
            return;
        }
        for (LevelExit exit : exits) {
            if (!exit.isInitiallyLocked() || !progress.isDoorOpen(exit.getDoorStateId())) {
                continue;
            }
            Rectangle2D bounds = exit.getBounds();
            for (int row = 0; row < tiles.length; row++) {
                for (int col = 0; col < tiles[row].length; col++) {
                    if (tiles[row][col] == TileType.DOOR_LOCKED
                            && bounds.contains(GameConfig.toPlayX(col) + GameConfig.TILE_SIZE / 2.0,
                                    GameConfig.toPlayY(row) + GameConfig.TILE_SIZE / 2.0)) {
                        tiles[row][col] = TileType.EXIT;
                    }
                }
            }
        }
    }

    /**
     * @return immutable list of enemies
     */
    public List<Enemy> getEnemies() {
        return Collections.unmodifiableList(enemies);
    }

    /**
     * @return stream of active enemies, used for gameplay and score calculations
     */
    public Stream<Enemy> activeEnemies() {
        return enemies.stream().filter(Enemy::isAlive);
    }

    /**
     * @return number of still active enemies
     */
    public long countActiveEnemies() {
        return activeEnemies().count();
    }

    /**
     * @return immutable list of item drops
     */
    public List<ItemDrop> getItemDrops() {
        return Collections.unmodifiableList(itemDrops);
    }

    /**
     * @return stream of uncollected item drops
     */
    public Stream<ItemDrop> activeItemDrops() {
        return itemDrops.stream().filter(drop -> !drop.isCollected());
    }

    /**
     * @return immutable list of rupee pickups
     */
    public List<RupeePickup> getRupees() {
        return Collections.unmodifiableList(rupees);
    }

    /**
     * @return stream of uncollected rupees
     */
    public Stream<RupeePickup> activeRupees() {
        return rupees.stream().filter(rupee -> !rupee.isCollected());
    }

    /**
     * @return mutable projectile list used by model update
     */
    public List<Projectile> getProjectiles() {
        return projectiles;
    }

    /**
     * Adds an enemy projectile to this level.
     *
     * @param projectile projectile to add
     */
    public void addProjectile(Projectile projectile) {
        projectiles.add(projectile);
    }

    /**
     * Removes dead projectiles.
     */
    public void removeInactiveProjectiles() {
        projectiles.removeIf(projectile -> !projectile.isAlive());
    }

    /**
     * @return player start x coordinate
     */
    public double getStartX() {
        return startX;
    }

    /**
     * @return player start y coordinate
     */
    public double getStartY() {
        return startY;
    }

    /**
     * @return immutable list of configured exits.
     */
    public List<LevelExit> getExits() {
        return Collections.unmodifiableList(exits);
    }

    /**
     * Returns the first exit matching the supplied id.
     *
     * @param id exit identifier
     * @return matching exit
     */
    public Optional<LevelExit> findExitById(String id) {
        return exits.stream().filter(exit -> exit.getId().equals(id)).findFirst();
    }

    /**
     * Legacy compatibility accessor used by older single-exit transitions.
     *
     * @return legacy exit bounds if available, otherwise {@code null}
     */
    public Rectangle2D.Double getExitBounds() {
        return legacyExitBounds;
    }

    /**
     * @return level background image
     */
    public BufferedImage getBackgroundImage() {
        return backgroundImage;
    }

    /**
     * @return {@code true} if the level exit requires a key
     */
    public boolean requiresKey() {
        return requiresLegacyKey;
    }

    /**
     * Returns the canonical level identifier used for graph navigation.
     * Example: {@code level1}.
     *
     * @return canonical level id
     */
    public String getLevelId() {
        return "level" + id;
    }

    /**
     * @return {@code true} if the level was already completed
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Marks the level completed.
     */
    public void markCompleted() {
        completed = true;
    }

    /**
     * @return {@code true} when all enemies have been defeated
     */
    public boolean allEnemiesDefeated() {
        return enemies.stream().allMatch(enemy -> !enemy.isAlive());
    }

    /**
     * @return {@code true} when the level is using the old single-exit completion pattern.
     */
    public boolean hasLegacyExits() {
        return exits.stream().anyMatch(LevelExit::isLegacy);
    }
}
