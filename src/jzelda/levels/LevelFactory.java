package jzelda.levels;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import jzelda.entities.EntityFactory;
import jzelda.items.ItemFactory;
import jzelda.model.GameConfig;
import jzelda.resources.ResourceManager;

/**
 * Factory Method loader for playable levels. It parses ASCII maps stored under
 * {@code resources/maps/} and creates enemies, items and collision tiles.
 */
public class LevelFactory {
    private final ResourceManager resources;
    private final EntityFactory entityFactory = new EntityFactory();
    private final ItemFactory itemFactory = new ItemFactory();

    /**
     * Creates a level factory.
     *
     * @param resources resource manager used to read map files
     */
    public LevelFactory(ResourceManager resources) {
        this.resources = resources;
    }

    /**
     * Loads a level from {@code resources/maps/levelN.map}.
     *
     * @param id level number between 1 and 8
     * @return parsed level
     */
    public Level createLevel(int id) {
        String filename = "level" + id + ".map";
        List<String> lines = resources.loadMapLines(filename);
        validate(lines, filename);
        TileType[][] tiles = new TileType[GameConfig.MAP_ROWS][GameConfig.MAP_COLUMNS];
        List<jzelda.entities.Enemy> enemies = new ArrayList<>();
        List<ItemDrop> itemDrops = new ArrayList<>();
        List<RupeePickup> rupees = new ArrayList<>();
        double startX = GameConfig.TILE_SIZE;
        double startY = GameConfig.TILE_SIZE;
        Rectangle2D.Double exit = new Rectangle2D.Double(GameConfig.PLAY_WIDTH - GameConfig.TILE_SIZE * 2,
                GameConfig.PLAY_HEIGHT - GameConfig.TILE_SIZE * 2, GameConfig.TILE_SIZE, GameConfig.TILE_SIZE);
        for (int row = 0; row < GameConfig.MAP_ROWS; row++) {
            String line = lines.get(row);
            for (int col = 0; col < GameConfig.MAP_COLUMNS; col++) {
                char c = line.charAt(col);
                double x = col * GameConfig.TILE_SIZE + 4;
                double y = row * GameConfig.TILE_SIZE + 4;
                tiles[row][col] = TileType.FLOOR;
                switch (c) {
                case '#':
                    tiles[row][col] = TileType.WALL;
                    break;
                case 'X':
                    tiles[row][col] = TileType.EXIT;
                    exit = new Rectangle2D.Double(col * GameConfig.TILE_SIZE + 4, row * GameConfig.TILE_SIZE + 4, 24, 24);
                    break;
                case 'P':
                    startX = x;
                    startY = y;
                    break;
                case 'A':
                case 'W':
                    enemies.add(entityFactory.createEnemy(c, x, y));
                    break;
                case 'R':
                    rupees.add(new RupeePickup(col * GameConfig.TILE_SIZE + 8, row * GameConfig.TILE_SIZE + 8,
                            id >= 6 ? 5 : 3));
                    break;
                case 'H':
                case 'K':
                case 'S':
                case 'L':
                case 'B':
                    itemDrops.add(new ItemDrop(itemFactory.createItem(c), col * GameConfig.TILE_SIZE,
                            row * GameConfig.TILE_SIZE));
                    break;
                default:
                    tiles[row][col] = TileType.FLOOR;
                    break;
                }
            }
        }
        boolean requiresKey = id % 2 == 0;
        return new Level(id, "Livello " + id + " - " + levelName(id), tiles, enemies, itemDrops, rupees, startX, startY,
                exit, requiresKey);
    }

    private void validate(List<String> lines, String filename) {
        if (lines.size() != GameConfig.MAP_ROWS) {
            throw new IllegalStateException(filename + " deve avere " + GameConfig.MAP_ROWS + " righe");
        }
        for (String line : lines) {
            if (line.length() != GameConfig.MAP_COLUMNS) {
                throw new IllegalStateException(filename + " deve avere righe di " + GameConfig.MAP_COLUMNS + " caratteri");
            }
        }
    }

    private String levelName(int id) {
        switch (id) {
        case 1:
            return "Bosco del Crepuscolo";
        case 2:
            return "Rovine della Fonte";
        case 3:
            return "Caverna delle Lanterne";
        case 4:
            return "Ponte dei Guardiani";
        case 5:
            return "Palude di Vetro";
        case 6:
            return "Miniere del Vento";
        case 7:
            return "Tempio Spezzato";
        case 8:
            return "Torre dell'Alba";
        default:
            return "Sconosciuto";
        }
    }
}
