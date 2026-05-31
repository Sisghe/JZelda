package jzelda.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import jzelda.entities.Direction;
import jzelda.entities.Enemy;
import jzelda.entities.Player;
import jzelda.entities.Projectile;
import jzelda.levels.ItemDrop;
import jzelda.levels.Level;
import jzelda.levels.RupeePickup;
import jzelda.levels.TileType;
import jzelda.model.GameConfig;
import jzelda.model.GameModel;
import jzelda.model.VisualEffect;
import jzelda.persistence.Profile;
import jzelda.persistence.ScoreEntry;
import jzelda.resources.ResourceManager;
import jzelda.shop.ShopOffer;

/**
 * Swing panel responsible for all drawing. It reads a snapshot-like view of the
 * model and paints HUD, map, sprites, menus, shop and visual effects.
 */
public class GamePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final GameModel model;
    private final ResourceManager resources;
    private final Font pixelFont = new Font(Font.MONOSPACED, Font.BOLD, 14);
    private final Font titleFont = new Font(Font.MONOSPACED, Font.BOLD, 30);

    /**
     * Creates a panel for a model.
     *
     * @param model model to render
     */
    public GamePanel(GameModel model) {
        this.model = model;
        this.resources = model.getFacade().resources();
        setPreferredSize(new Dimension(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT));
        setFocusable(true);
        setDoubleBuffered(true);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setFont(pixelFont);
        drawBackground(g);
        drawHud(g);
        if ("PLAYING".equals(model.getStateName()) || "PAUSED".equals(model.getStateName())) {
            drawLevel(g);
            drawEntities(g);
            drawEffects(g);
        }
        drawOverlays(g);
        g.dispose();
    }

    private void drawBackground(Graphics2D g) {
        g.setColor(RetroPalette.INK);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private void drawHud(Graphics2D g) {
        g.setColor(RetroPalette.HUD);
        g.fillRect(0, 0, GameConfig.WINDOW_WIDTH, GameConfig.HUD_HEIGHT);
        g.setColor(RetroPalette.TEXT);
        Profile profile = model.getFacade().profiles().getCurrentProfile();
        String nickname = profile == null ? "-" : profile.getNickname();
        g.drawString("Profilo: " + nickname, 12, 20);
        g.drawString("Stato: " + model.getStateName(), 12, 40);
        if (model.getCurrentLevel() != null) {
            g.drawString("Lv " + model.getLevelManager().getCurrentNumber() + ": " + model.getCurrentLevel().getName(), 12, 60);
        }
        g.drawString("Score " + model.getScore(), 360, 20);
        g.drawString("Rupie " + model.getPlayer().getRupees(), 360, 40);
        g.drawString("Vite " + model.getPlayer().getLives() + "  Continue " + model.getContinuesLeft(), 360, 60);
        drawHearts(g, 360, 78, model.getPlayer().getHealth(), model.getPlayer().getMaxHealth());
        g.setColor(RetroPalette.GOLD);
        g.drawString(model.getMessage(), 12, 84);
    }

    private void drawHearts(Graphics2D g, int x, int y, int health, int maxHealth) {
        for (int i = 0; i < maxHealth; i++) {
            g.setColor(i < health ? RetroPalette.RED : Color.DARK_GRAY);
            g.fillRect(x + i * 11, y, 8, 8);
        }
    }

    private void drawLevel(Graphics2D g) {
        Level level = model.getCurrentLevel();
        int offsetY = GameConfig.HUD_HEIGHT;
        for (int row = 0; row < GameConfig.MAP_ROWS; row++) {
            for (int col = 0; col < GameConfig.MAP_COLUMNS; col++) {
                int x = col * GameConfig.TILE_SIZE;
                int y = offsetY + row * GameConfig.TILE_SIZE;
                TileType tile = level.getTile(col, row);
                if (tile == TileType.WALL) {
                    g.setColor(RetroPalette.WALL);
                    g.fillRect(x, y, GameConfig.TILE_SIZE, GameConfig.TILE_SIZE);
                    g.setColor(RetroPalette.INK);
                    g.drawRect(x + 3, y + 3, GameConfig.TILE_SIZE - 6, GameConfig.TILE_SIZE - 6);
                } else {
                    g.setColor(RetroPalette.FLOOR);
                    g.fillRect(x, y, GameConfig.TILE_SIZE, GameConfig.TILE_SIZE);
                    g.setColor(new Color(0, 0, 0, 28));
                    g.drawRect(x, y, GameConfig.TILE_SIZE, GameConfig.TILE_SIZE);
                }
                if (tile == TileType.EXIT) {
                    g.setColor(level.allEnemiesDefeated() ? RetroPalette.GOLD : Color.GRAY);
                    g.fillRect(x + 7, y + 7, 18, 18);
                    g.setColor(RetroPalette.INK);
                    g.drawRect(x + 7, y + 7, 18, 18);
                }
            }
        }
    }

    private void drawEntities(Graphics2D g) {
        int offsetY = GameConfig.HUD_HEIGHT;
        Level level = model.getCurrentLevel();
        for (RupeePickup rupee : level.getRupees()) {
            if (!rupee.isCollected()) {
                drawRupee(g, (int) rupee.getX(), offsetY + (int) rupee.getY());
            }
        }
        for (ItemDrop drop : level.getItemDrops()) {
            if (!drop.isCollected()) {
                drawSprite(g, drop.getItem().getSpriteKey(), (int) drop.getX() + 4, offsetY + (int) drop.getY() + 4, 24, 24);
            }
        }
        for (Projectile projectile : level.getProjectiles()) {
            drawProjectile(g, projectile, offsetY);
        }
        for (Enemy enemy : level.getEnemies()) {
            if (enemy.isAlive()) {
                drawEnemy(g, enemy, offsetY);
            }
        }
        drawPlayer(g, model.getPlayer(), offsetY);
        if (model.isAttackFlashVisible()) {
            Rectangle2D.Double attack = model.getLastAttackBounds();
            g.setColor(new Color(255, 255, 255, 120));
            g.fillRect((int) attack.x, offsetY + (int) attack.y, (int) attack.width, (int) attack.height);
        }
    }

    private void drawSprite(Graphics2D g, String key, int x, int y, int w, int h) {
        Image sprite = resources.getSprite(key);
        g.drawImage(sprite, x, y, w, h, this);
    }

    private void drawRupee(Graphics2D g, int x, int y) {
        g.setColor(RetroPalette.GREEN);
        int[] xs = { x + 8, x + 16, x + 8, x };
        int[] ys = { y, y + 8, y + 16, y + 8 };
        g.fillPolygon(xs, ys, 4);
        g.setColor(RetroPalette.INK);
        g.drawPolygon(xs, ys, 4);
    }

    private void drawProjectile(Graphics2D g, Projectile projectile, int offsetY) {
        g.setColor(RetroPalette.BLUE);
        g.fillOval((int) projectile.getX(), offsetY + (int) projectile.getY(), projectile.getWidth(), projectile.getHeight());
        g.setColor(Color.BLACK);
        g.drawOval((int) projectile.getX(), offsetY + (int) projectile.getY(), projectile.getWidth(), projectile.getHeight());
    }

    private void drawEnemy(Graphics2D g, Enemy enemy, int offsetY) {
        String key = enemy.getSpriteKey() + enemy.getAnimationFrame();
        drawSprite(g, key, (int) enemy.getX(), offsetY + (int) enemy.getY(), 28, 28);
        g.setColor(Color.BLACK);
        g.drawRect((int) enemy.getX(), offsetY + (int) enemy.getY(), enemy.getWidth(), enemy.getHeight());
    }

    private void drawPlayer(Graphics2D g, Player player, int offsetY) {
        boolean flash = player.isInvulnerable() && ((model.getTitleClockMs() / 80) % 2 == 0);
        if (!flash) {
            String key = "player" + player.getAnimationFrame();
            drawSprite(g, key, (int) player.getX(), offsetY + (int) player.getY(), 28, 28);
        }
        if (player.hasShield()) {
            g.setColor(new Color(120, 190, 255, 90));
            g.fillOval((int) player.getX() - 4, offsetY + (int) player.getY() - 4, 32, 32);
        }
        drawDirectionMarker(g, player, offsetY);
    }

    private void drawDirectionMarker(Graphics2D g, Player player, int offsetY) {
        g.setColor(RetroPalette.GOLD);
        int cx = (int) player.getX() + 12;
        int cy = offsetY + (int) player.getY() + 12;
        Direction direction = player.getDirection();
        if (direction == Direction.UP) {
            g.drawLine(cx, cy, cx, cy - 12);
        } else if (direction == Direction.DOWN) {
            g.drawLine(cx, cy, cx, cy + 12);
        } else if (direction == Direction.LEFT) {
            g.drawLine(cx, cy, cx - 12, cy);
        } else if (direction == Direction.RIGHT) {
            g.drawLine(cx, cy, cx + 12, cy);
        }
    }

    private void drawEffects(Graphics2D g) {
        int offsetY = GameConfig.HUD_HEIGHT;
        g.setColor(Color.WHITE);
        for (VisualEffect effect : model.getEffects()) {
            int y = offsetY + (int) effect.getY() - (int) (effect.getProgress() * 20);
            g.drawString(effect.getText(), (int) effect.getX(), y);
        }
    }

    private void drawOverlays(Graphics2D g) {
        String state = model.getStateName();
        if ("MENU".equals(state)) {
            drawMenu(g, "JZELDA", "Avventura action retrò originale", true);
        } else if ("PROFILE".equals(state)) {
            drawMenu(g, "PROFILI", "N = nuovo profilo, Invio = seleziona", true);
            drawProfileStats(g);
        } else if ("PAUSED".equals(state)) {
            drawDim(g);
            drawMenu(g, "PAUSA", "Inventario: " + formatInventory(model.getPlayer().getInventory()), false);
        } else if ("SHOP".equals(state)) {
            drawDim(g);
            drawShop(g);
        } else if ("GAME_OVER".equals(state)) {
            drawDim(g);
            drawCenteredTitle(g, "GAME OVER", "C = continua, Esc = menu");
        } else if ("LEADERBOARD".equals(state)) {
            drawDim(g);
            drawLeaderboard(g);
        } else if ("VICTORY".equals(state)) {
            drawDim(g);
            drawCenteredTitle(g, "VITTORIA", "Score finale: " + model.getScore());
        }
    }

    private String formatInventory(Map<String, Integer> inventory) {
        if (inventory.isEmpty()) {
            return "vuoto";
        }
        StringBuilder builder = new StringBuilder();
        inventory.forEach((key, value) -> builder.append(key).append('x').append(value).append(' '));
        return builder.toString();
    }

    private void drawDim(Graphics2D g) {
        g.setColor(RetroPalette.SHADOW);
        g.fillRect(0, GameConfig.HUD_HEIGHT, GameConfig.WINDOW_WIDTH, GameConfig.PLAY_HEIGHT);
    }

    private void drawMenu(Graphics2D g, String title, String subtitle, boolean fullscreen) {
        int x = fullscreen ? 70 : 110;
        int y = fullscreen ? 130 : 150;
        int w = fullscreen ? 500 : 420;
        int h = fullscreen ? 330 : 260;
        drawBox(g, x, y, w, h);
        g.setFont(titleFont);
        g.setColor(RetroPalette.GOLD);
        g.drawString(title, x + 30, y + 50);
        g.setFont(pixelFont);
        g.setColor(RetroPalette.TEXT);
        g.drawString(subtitle, x + 30, y + 78);
        List<String> options = model.getMenuOptions();
        for (int i = 0; i < options.size(); i++) {
            int oy = y + 118 + i * 30;
            g.setColor(i == model.getMenuIndex() ? RetroPalette.GOLD : RetroPalette.TEXT);
            g.drawString((i == model.getMenuIndex() ? "> " : "  ") + options.get(i), x + 42, oy);
        }
        g.setColor(RetroPalette.BLUE);
        g.drawString("Frecce/WASD: muovi  Spazio: attacca  P: pausa  E/Invio: conferma", 36, GameConfig.WINDOW_HEIGHT - 18);
    }

    private void drawBox(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(20, 26, 34, 230));
        g.fillRect(x, y, w, h);
        g.setStroke(new BasicStroke(3));
        g.setColor(RetroPalette.TEXT);
        g.drawRect(x, y, w, h);
        g.setStroke(new BasicStroke(1));
    }

    private void drawProfileStats(Graphics2D g) {
        Profile profile = model.getFacade().profiles().getCurrentProfile();
        if (profile != null) {
            g.setColor(RetroPalette.BLUE);
            g.drawString("Totale partite profili: " + model.getFacade().profiles().calculateTotalGames(), 105, 430);
            g.drawString("Profilo corrente: " + profile.getNickname() + "  WinRate: " + String.format("%.1f", profile.getWinRate()) + "%", 105,
                    452);
        }
    }

    private void drawShop(Graphics2D g) {
        drawBox(g, 80, 130, 480, 330);
        g.setFont(titleFont);
        g.setColor(RetroPalette.GOLD);
        g.drawString("BOTTEGA", 110, 180);
        g.setFont(pixelFont);
        g.setColor(RetroPalette.TEXT);
        g.drawString("Rupie disponibili: " + model.getPlayer().getRupees(), 110, 210);
        int y = 240;
        for (ShopOffer offer : model.getShop().getOffers()) {
            boolean affordable = offer.getPrice() <= model.getPlayer().getRupees();
            g.setColor(affordable ? RetroPalette.TEXT : Color.GRAY);
            g.drawString(offer.getLabel() + "  costo " + offer.getPrice(), 120, y);
            y += 28;
        }
        g.setColor(RetroPalette.BLUE);
        g.drawString("Invio/Esc: esci. La bottega è separata dagli 8 livelli.", 110, 420);
    }

    private void drawLeaderboard(Graphics2D g) {
        drawBox(g, 80, 100, 500, 390);
        g.setFont(titleFont);
        g.setColor(RetroPalette.GOLD);
        g.drawString("CLASSIFICA", 110, 150);
        g.setFont(pixelFont);
        int y = 190;
        List<ScoreEntry> top = model.getFacade().leaderboard().topScores(10);
        if (top.isEmpty()) {
            g.setColor(RetroPalette.TEXT);
            g.drawString("Nessun punteggio salvato.", 120, y);
        } else {
            for (int i = 0; i < top.size(); i++) {
                ScoreEntry entry = top.get(i);
                g.setColor(i == 0 ? RetroPalette.GOLD : RetroPalette.TEXT);
                g.drawString((i + 1) + ". " + entry.getNickname() + "  " + entry.getScore() + "  Lv " + entry.getLevelReached()
                        + (entry.isVictory() ? "  WIN" : "  LOST"), 110, y + i * 28);
            }
        }
        g.setColor(RetroPalette.BLUE);
        g.drawString("Invio/Esc per tornare al menu", 110, 462);
    }

    private void drawCenteredTitle(Graphics2D g, String title, String subtitle) {
        drawBox(g, 105, 180, 430, 190);
        g.setFont(titleFont);
        g.setColor(RetroPalette.GOLD);
        g.drawString(title, 145, 245);
        g.setFont(pixelFont);
        g.setColor(RetroPalette.TEXT);
        g.drawString(subtitle, 145, 285);
    }
}
