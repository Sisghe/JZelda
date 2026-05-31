package jzelda.levels;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final Rectangle2D.Double exitBounds;
    private final boolean requiresKey;
    private boolean completed;

    /**
     * Creates a level from parsed map data.
     *
     * @param id level number
     * @param name display name
     * @param tiles tile matrix
     * @param enemies enemies contained in the level
     * @param itemDrops item pickups contained in the level
     * @param rupees rupee pickups contained in the level
     * @param startX player start x coordinate
     * @param startY player start y coordinate
     * @param exitBounds exit collision rectangle
     * @param requiresKey whether a key is required to complete the level
     */
    public Level(int id, String name, TileType[][] tiles, List<Enemy> enemies, List<ItemDrop> itemDrops,
            List<RupeePickup> rupees, double startX, double startY, Rectangle2D.Double exitBounds, boolean requiresKey) {
        this.id = id;
        this.name = name;
        this.tiles = tiles;
        this.enemies = new ArrayList<>(enemies);
        this.itemDrops = new ArrayList<>(itemDrops);
        this.rupees = new ArrayList<>(rupees);
        this.startX = startX;
        this.startY = startY;
        this.exitBounds = exitBounds;
        this.requiresKey = requiresKey;
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
        if (rectangle.getMinX() < 0 || rectangle.getMinY() < 0 || rectangle.getMaxX() >= GameConfig.PLAY_WIDTH
                || rectangle.getMaxY() >= GameConfig.PLAY_HEIGHT) {
            return true;
        }
        int minCol = (int) Math.floor(rectangle.getMinX() / GameConfig.TILE_SIZE);
        int maxCol = (int) Math.floor((rectangle.getMaxX() - 1) / GameConfig.TILE_SIZE);
        int minRow = (int) Math.floor(rectangle.getMinY() / GameConfig.TILE_SIZE);
        int maxRow = (int) Math.floor((rectangle.getMaxY() - 1) / GameConfig.TILE_SIZE);
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                if (getTile(col, row).isBlocked()) {
                    return true;
                }
            }
        }
        return false;
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
     * @return exit bounds
     */
    public Rectangle2D.Double getExitBounds() {
        return exitBounds;
    }

    /**
     * @return {@code true} if the level exit requires a key
     */
    public boolean requiresKey() {
        return requiresKey;
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
}
