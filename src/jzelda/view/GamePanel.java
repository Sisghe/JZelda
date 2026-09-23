package jzelda.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JPanel;
import jzelda.entities.Direction;
import jzelda.entities.Enemy;
import jzelda.entities.Player;
import jzelda.entities.Projectile;
import jzelda.levels.DoorVisualState;
import jzelda.levels.DungeonLayout;
import jzelda.levels.DungeonProgress;
import jzelda.levels.ExitType;
import jzelda.levels.ItemDrop;
import jzelda.levels.Level;
import jzelda.levels.LevelExit;
import jzelda.levels.RupeePickup;
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
    private static final int PLAYER_DRAW_SIZE = 28;
    private static final int PLAYER_ATTACK_CANVAS_WIDTH = 70;
    private static final int PLAYER_ATTACK_CANVAS_HEIGHT = 115;
    private static final int PLAYER_ATTACK_BODY_CANVAS_WIDTH = 70;
    private static final double PLAYER_BODY_SCALE = (double) PLAYER_DRAW_SIZE / PLAYER_ATTACK_CANVAS_WIDTH;
    private static final int PLAYER_ATTACK_RENDER_WIDTH = PLAYER_DRAW_SIZE;
    private static final int PLAYER_ATTACK_RENDER_HEIGHT = (int) Math
            .round((float) PLAYER_ATTACK_RENDER_WIDTH * PLAYER_ATTACK_CANVAS_HEIGHT / (float) PLAYER_ATTACK_CANVAS_WIDTH);
    private static final int SWORD_ATTACK_FRAME_COUNT = 5;
    private static final long SWORD_ATTACK_FRAME_DURATION_MS = 52L;
    private static final boolean DRAW_DIRECTION_MARKER = false;
    private static final boolean DEBUG_PLAYER_ATTACK_METRICS = false;
    private static final double MOVEMENT_EPSILON = 0.001D;

    private final GameModel model;
    private final ResourceManager resources;
    private final Set<String> debugMetricsLogged = new HashSet<>();
    private final Font pixelFont = new Font(Font.MONOSPACED, Font.BOLD, 14);
    private final Font titleFont = new Font(Font.MONOSPACED, Font.BOLD, 30);

    private double lastPlayerX = Double.NaN;
    private double lastPlayerY = Double.NaN;
    private int lastMovementFrame = 0;

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

        /*
        * Riempie tutta la finestra reale.
        * Quando la finestra viene ingrandita, le eventuali bande laterali
        * restano dello stesso colore dello sfondo.
        */
        g.setColor(RetroPalette.INK);
        g.fillRect(0, 0, getWidth(), getHeight());

        /*
        * Scala il gioco mantenendo le proporzioni originali.
        * Il rendering interno continua a ragionare in coordinate logiche:
        * GameConfig.WINDOW_WIDTH x GameConfig.WINDOW_HEIGHT.
        */
        double scaleX = getWidth() / (double) GameConfig.WINDOW_WIDTH;
        double scaleY = getHeight() / (double) GameConfig.WINDOW_HEIGHT;
        double scale = Math.min(scaleX, scaleY);

        int scaledWidth = (int) Math.round(GameConfig.WINDOW_WIDTH * scale);
        int scaledHeight = (int) Math.round(GameConfig.WINDOW_HEIGHT * scale);

        int offsetX = (getWidth() - scaledWidth) / 2;
        int offsetY = (getHeight() - scaledHeight) / 2;

        /*
        * Da qui in poi tutto il gioco viene disegnato centrato e scalato.
        */
        g.translate(offsetX, offsetY);
        g.scale(scale, scale);

        /*
        * Mantiene l'effetto pixel art evitando interpolazione morbida.
        */
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        g.setClip(0, 0, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        g.setFont(pixelFont);

        drawBackground(g);
        drawHud(g);

        if ("PLAYING".equals(model.getStateName()) || "PAUSED".equals(model.getStateName())
            || "DOOR_PROMPT".equals(model.getStateName()) || "DOOR_OPENING".equals(model.getStateName())) {
            drawLevel(g);
            drawEntities(g);
            drawEffects(g);
        }

        drawOverlays(g);

        g.dispose();
    }

    private void drawBackground(Graphics2D g) {
        g.setColor(RetroPalette.INK);
        g.fillRect(0, 0, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
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
        if (level == null || level.getBackgroundImage() == null) {
            return;
        }
        int levelX = GameConfig.ROOM_OFFSET_X;
        int levelY = GameConfig.HUD_HEIGHT + GameConfig.ROOM_OFFSET_Y;
        Image background = resolveRoomBackground(level);
        g.drawImage(background, levelX, levelY, GameConfig.ROOM_WIDTH, GameConfig.ROOM_HEIGHT, this);
        drawDoorOverlays(g, level);
    }

    private Image resolveRoomBackground(Level level) {
        String roomId = model.getLevelManager().getCurrentRoomId();
        if (roomId == null) {
            return level.getBackgroundImage();
        }
        String spriteName = model.getCurrentRoomSpriteName();
        if (roomId.equals(spriteName)) {
            return level.getBackgroundImage();
        }
        Image sprite = resources.loadRoomSprite(spriteName);
        return sprite == null ? level.getBackgroundImage() : sprite;
    }

    private void drawDoorOverlays(Graphics2D g, Level level) {
        String roomId = model.getLevelManager().getCurrentRoomId();
        if (roomId == null) {
            return;
        }
        for (LevelExit exit : level.getExits()) {
            if (!exit.isInitiallyLocked() || exit.getVisualId() == null || exit.getMapRow() < 0 || exit.getMapColumn() < 0) {
                continue;
            }
            DoorVisualState state = model.getDoorVisualState(exit);
            Image overlay = resources.getDoorSprite(roomId, exit.getVisualId(), state, model.getDoorOpeningFrame());
            if (overlay == null) {
                continue;
            }
            int x = GameConfig.toPlayX(exit.getMapColumn());
            int y = GameConfig.toPlayY(exit.getMapRow());
            int width = Math.max(1, overlay.getWidth(this));
            int height = Math.max(1, overlay.getHeight(this));
            g.drawImage(overlay, x, y, width, height, this);
        }
    }

    private void drawEntities(Graphics2D g) {
        Level level = model.getCurrentLevel();
        for (RupeePickup rupee : level.getRupees()) {
            if (!rupee.isCollected()) {
                drawRupee(g, toScreenX(rupee.getX()), toScreenY(rupee.getY()));
            }
        }
        for (ItemDrop drop : level.getItemDrops()) {
            if (!drop.isCollected()) {
                drawSprite(g, drop.getItem().getSpriteKey(), toScreenX(drop.getX()) + 4, toScreenY(drop.getY()) + 4, 24,
                        24);
            }
        }
        for (Projectile projectile : level.getProjectiles()) {
            drawProjectile(g, projectile);
        }
        for (Enemy enemy : level.getEnemies()) {
            if (enemy.isAlive()) {
                drawEnemy(g, enemy);
            }
        }
        drawPlayer(g, model.getPlayer());
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

    private void drawProjectile(Graphics2D g, Projectile projectile) {
        g.setColor(RetroPalette.BLUE);
        g.fillOval(toScreenX(projectile.getX()), toScreenY(projectile.getY()), projectile.getWidth(), projectile.getHeight());
        g.setColor(Color.BLACK);
        g.drawOval(toScreenX(projectile.getX()), toScreenY(projectile.getY()), projectile.getWidth(), projectile.getHeight());
    }

    private void drawEnemy(Graphics2D g, Enemy enemy) {
        String base = enemy.getSpriteKey();
        if ("enemy_reaper".equals(base)) {
            // Reaper uses oversized sprites located under images/enemies/reaper/
            String dir = directionKey(enemy.getDirection());
            String key;
            if (enemy.isAttacking()) {
                key = "enemies/reaper/enemy_reaper_" + dir + "_attack_" + enemy.getAttackFrame();
            } else {
                int frame = enemy.isMoving() ? enemy.getAnimationFrame() : 0;
                key = "enemies/reaper/enemy_reaper_" + dir + "_" + frame;
            }
            Image sprite = resources.getSprite(key);
            // Compute scaled sizes keeping the same scale used for player attack sprites
            double scale = PLAYER_BODY_SCALE; // 28 / 70-ish, suitable for our assets
            int srcW = sprite == null ? 0 : sprite.getWidth(null);
            int srcH = sprite == null ? 0 : sprite.getHeight(null);
            int drawW = srcW <= 0 ? 28 : Math.max(1, (int) Math.round(srcW * scale));
            int drawH = srcH <= 0 ? 28 : Math.max(1, (int) Math.round(srcH * scale));
            // center sprite on the enemy collision body's center to avoid oscillation
            int centerX = toScreenX(enemy.getX()) + enemy.getWidth() / 2;
            int centerY = toScreenY(enemy.getY()) + enemy.getHeight() / 2;
            int renderX = centerX - drawW / 2;
            int renderY = centerY - drawH / 2;

            if (sprite != null) {
                g.drawImage(sprite, renderX, renderY, drawW, drawH, this);
            } else {
                drawSprite(g, key, renderX, renderY, drawW, drawH);
            }
            g.setColor(Color.BLACK);
            g.drawRect(toScreenX(enemy.getX()), toScreenY(enemy.getY()), enemy.getWidth(), enemy.getHeight());
            return;
        }

        String key = enemy.getSpriteKey() + enemy.getAnimationFrame();
        drawSprite(g, key, toScreenX(enemy.getX()), toScreenY(enemy.getY()), 28, 28);
        g.setColor(Color.BLACK);
        g.drawRect(toScreenX(enemy.getX()), toScreenY(enemy.getY()), enemy.getWidth(), enemy.getHeight());
    }

    private void drawPlayer(Graphics2D g, Player player) {
        boolean flash = player.isInvulnerable() && ((model.getTitleClockMs() / 80) % 2 == 0);
        if (!flash) {
            String key = playerSpriteKey(player);
            drawPlayerSprite(g, key, player);
        }
        if (DRAW_DIRECTION_MARKER) {
            drawDirectionMarker(g, player);
        }
    }

    private void drawPlayerSprite(Graphics2D g, String key, Player player) {
        int x = toScreenX(player.getX());
        int y = toScreenY(player.getY());
        int feetX = x + PLAYER_DRAW_SIZE / 2;
        int feetY = y + PLAYER_DRAW_SIZE;
        int playerCenterX = x + PLAYER_DRAW_SIZE / 2;

        Direction direction = model.isAttackFlashVisible() ? model.getAttackDirection() : player.getDirection();

        if (isUnarmedAttackSprite(key)) {
            drawUnarmedAttackSprite(g, key, direction, x, y, feetY, playerCenterX);
            return;
        }

        /*
         * Movement, shield and unarmed attack sprites are always drawn in the
         * player's single tile. This keeps oversized exported PNG canvases from
         * making the character appear huge.
         */
        if (!isSwordAttackSprite(key)) {
            drawSprite(g, key, x, y, PLAYER_DRAW_SIZE, PLAYER_DRAW_SIZE);
            return;
        }

        /*
         * Sword attacks visually occupy the player's tile plus the next tile in
         * the attack direction, while the player's logical collision box remains
         * unchanged.
         */
        int renderX = x;
        int renderY = y;

        if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            Image attackSprite = resources.getSprite(key);
            int[] targetSize = computeUniformAttackDrawSize(attackSprite);
            int drawWidth = targetSize[0];
            int drawHeight = targetSize[1];

            int bodyPivotX = estimateAttackBodyPivotX(direction, attackSprite);
            int scaledBodyPivotX = (int) Math.round(bodyPivotX * PLAYER_BODY_SCALE);
            int leftAnchorX = playerCenterX - scaledBodyPivotX;

            renderX = leftAnchorX;
            renderY = y;

            if (DEBUG_PLAYER_ATTACK_METRICS) {
                int srcW = attackSprite == null ? 0 : attackSprite.getWidth(null);
                int srcH = attackSprite == null ? 0 : attackSprite.getHeight(null);
                double scaleX = srcW <= 0 ? 0 : (double) drawWidth / srcW;
                double scaleY = srcH <= 0 ? 0 : (double) drawHeight / srcH;
                String frame = String.format(
                        "key=%s, direction=%s, frame=%d, src=%dx%d, draw=%dx%d, renderX=%d, renderY=%d, bodyPivotX=%d, scaleX=%.4f, scaleY=%.4f, abs=%+.4f",
                        key, direction.name().toLowerCase(), swordAttackFrame(),
                        srcW, srcH, drawWidth, drawHeight, renderX, renderY, bodyPivotX, scaleX, scaleY,
                        Math.abs(scaleX - scaleY));
                debugAttackFrameMetrics(frame);
            }

            if (attackSprite != null) {
                g.drawImage(attackSprite, renderX, renderY, drawWidth, drawHeight, this);
            } else {
                drawSprite(g, key, renderX, renderY, drawWidth, drawHeight);
            }
            return;
        }

        if (direction == Direction.DOWN) {
            Image attackSprite = resources.getSprite(key);
            int[] targetSize = computeUniformAttackDrawSize(attackSprite);
            int drawWidth = targetSize[0];
            int drawHeight = targetSize[1];
            int downRenderX = playerCenterX - (drawWidth / 2);
            int downRenderY = y;

            if (DEBUG_PLAYER_ATTACK_METRICS) {
                int srcW = attackSprite == null ? 0 : attackSprite.getWidth(null);
                int srcH = attackSprite == null ? 0 : attackSprite.getHeight(null);
                double scaleX = srcW <= 0 ? 0 : (double) drawWidth / srcW;
                double scaleY = srcH <= 0 ? 0 : (double) drawHeight / srcH;
                String frame = String.format(
                        "key=%s, direction=%s, src=%dx%d, draw=%dx%d, render=(%d,%d), scaleX=%.4f, scaleY=%.4f, abs=%+.4f",
                        key, direction.name().toLowerCase(), srcW, srcH, drawWidth, drawHeight, downRenderX, downRenderY,
                        scaleX, scaleY, Math.abs(scaleX - scaleY));
                debugAttackFrameMetrics(frame);
            }

            if (attackSprite != null) {
                g.drawImage(attackSprite, downRenderX, downRenderY, drawWidth, drawHeight, this);
            } else {
                drawSprite(g, key, downRenderX, downRenderY, drawWidth, drawHeight);
            }
            return;
        }

        Image sprite = resources.getSprite(key);
        int[] targetSize = computeUniformAttackDrawSize(sprite);
        int drawWidth = targetSize[0];
        int drawHeight = targetSize[1];
        renderX = playerCenterX - (drawWidth / 2);
        renderY = feetY - drawHeight;

        if (DEBUG_PLAYER_ATTACK_METRICS) {
            int srcW = sprite == null ? 0 : sprite.getWidth(null);
            int srcH = sprite == null ? 0 : sprite.getHeight(null);
            double scaleX = srcW <= 0 ? 0 : (double) drawWidth / srcW;
            double scaleY = srcH <= 0 ? 0 : (double) drawHeight / srcH;
            String frame = String.format(
                    "key=%s, direction=%s, src=%dx%d, draw=%dx%d, render=(%d,%d), scaleX=%.4f, scaleY=%.4f, abs=%+.4f",
                    key, direction.name().toLowerCase(), srcW, srcH, drawWidth, drawHeight, renderX, renderY, scaleX,
                    scaleY, Math.abs(scaleX - scaleY));
            debugAttackFrameMetrics(frame);
        }

        if (sprite != null) {
            g.drawImage(sprite, renderX, renderY, drawWidth, drawHeight, this);
        } else {
            drawSprite(g, key, renderX, renderY, drawWidth, drawHeight);
        }
    }

    private int estimateAttackBodyPivotX(Direction direction, Image sprite) {
        int srcW = sprite == null ? 0 : sprite.getWidth(null);
        int rightBodyPivot = Math.max(0, PLAYER_ATTACK_BODY_CANVAS_WIDTH / 2);

        if (srcW <= 0 || srcW <= rightBodyPivot) {
            return rightBodyPivot;
        }
        if (direction == Direction.LEFT) {
            return srcW - 1 - rightBodyPivot;
        }
        return rightBodyPivot;
    }

    private int[] computeUniformAttackDrawSize(Image sprite) {
        int srcW = sprite == null ? 0 : sprite.getWidth(null);
        int srcH = sprite == null ? 0 : sprite.getHeight(null);
        if (srcW <= 0 || srcH <= 0) {
            return new int[] { PLAYER_ATTACK_RENDER_WIDTH, PLAYER_ATTACK_RENDER_HEIGHT };
        }

        int drawW = Math.max(1, (int) Math.round(srcW * PLAYER_BODY_SCALE));
        int drawH = Math.max(1, (int) Math.round(srcH * PLAYER_BODY_SCALE));
        return new int[] { drawW, drawH };
    }

    private void debugAttackFrameMetrics(String frameLog) {
        if (!debugMetricsLogged.contains(frameLog)) {
            debugMetricsLogged.add(frameLog);
            System.out.println("[PLAYER_ATTACK_METRICS] " + frameLog);
        }
    }

    private boolean isSwordAttackSprite(String key) {
        return key != null && key.contains("_attack_sword_");
    }

    private boolean isUnarmedAttackSprite(String key) {
        return key != null && key.contains("_attack_unarmed_");
    }

    private void drawUnarmedAttackSprite(Graphics2D g, String key, Direction direction,
            int x, int y, int feetY, int playerCenterX) {
        Image sprite = resources.getSprite(key);
        int[] targetSize = computeUniformAttackDrawSize(sprite);
        int drawWidth = targetSize[0];
        int drawHeight = targetSize[1];
        int drawX;
        int drawY;

        if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            int bodyPivotX = estimateAttackBodyPivotX(direction, sprite);
            int scaledBodyPivotX = (int) Math.round(bodyPivotX * PLAYER_BODY_SCALE);
            drawX = playerCenterX - scaledBodyPivotX;
            drawY = y;
        } else if (direction == Direction.DOWN) {
            drawX = playerCenterX - drawWidth / 2;
            drawY = y;
        } else {
            drawX = playerCenterX - drawWidth / 2;
            drawY = feetY - drawHeight;
        }

        if (sprite != null) {
            g.drawImage(sprite, drawX, drawY, drawWidth, drawHeight, this);
        } else {
            drawSprite(g, key, drawX, drawY, drawWidth, drawHeight);
        }
    }

    private String playerSpriteKey(Player player) {
        if (model.isAttackFlashVisible()) {
            String attackDirection = directionKey(model.getAttackDirection());
            if (playerHasSword(player)) {
                return "player_" + attackDirection + "_attack_sword_" + swordAttackFrame();
            }
            return "player_" + attackDirection + "_attack_unarmed_" + unarmedAttackFrame();
        }

        String direction = directionKey(player.getDirection());
        if (player.hasShield() && player.getDirection() != Direction.UP) {
            return "player_" + direction + "_shield_" + player.getAnimationFrame();
        }

        int movementFrame = movementFrameFor(player);
        return "player_" + direction + "_" + movementFrame;
    }

    private int unarmedAttackFrame() {
        return model.getAttackAnimationElapsedMs() < model.getAttackAnimationDurationMs() / 2L ? 0 : 1;
    }

    private int movementFrameFor(Player player) {
        double currentX = player.getX();
        double currentY = player.getY();

        if (Double.isNaN(lastPlayerX) || Double.isNaN(lastPlayerY)) {
            lastPlayerX = currentX;
            lastPlayerY = currentY;
            lastMovementFrame = player.getAnimationFrame();
            return lastMovementFrame;
        }

        boolean moved = Math.abs(currentX - lastPlayerX) > MOVEMENT_EPSILON
                || Math.abs(currentY - lastPlayerY) > MOVEMENT_EPSILON;

        lastPlayerX = currentX;
        lastPlayerY = currentY;

        if (moved) {
            lastMovementFrame = player.getAnimationFrame();
        }

        return lastMovementFrame;
    }

    private String directionKey(Direction direction) {
        if (direction == Direction.UP) {
            return "up";
        }
        if (direction == Direction.LEFT) {
            return "left";
        }
        if (direction == Direction.RIGHT) {
            return "right";
        }
        return "down";
    }

    private int swordAttackFrame() {
        int frame = (int) (model.getAttackAnimationElapsedMs() / SWORD_ATTACK_FRAME_DURATION_MS);
        return Math.min(SWORD_ATTACK_FRAME_COUNT - 1, frame);
    }

    private boolean playerHasSword(Player player) {
        return player.hasSwordEquipped();
    }

    private void drawDirectionMarker(Graphics2D g, Player player) {
        g.setColor(RetroPalette.GOLD);
        int cx = toScreenX(player.getX()) + 12;
        int cy = toScreenY(player.getY()) + 12;
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
        g.setColor(Color.WHITE);
        for (VisualEffect effect : model.getEffects()) {
            int y = toScreenY(effect.getY()) - (int) (effect.getProgress() * 20);
            g.drawString(effect.getText(), toScreenX(effect.getX()), y);
        }
    }

    private int toScreenX(double worldX) {
        return (int) worldX;
    }

    private int toScreenY(double worldY) {
        return GameConfig.HUD_HEIGHT + (int) worldY;
    }

    private void drawOverlays(Graphics2D g) {
        // Draw minimap if toggled in the model
        if (model.isMinimapVisible()) {
            drawMinimap(g);
        }

        String state = model.getStateName();
        if ("MENU".equals(state)) {
            drawMenu(g, "JZELDA", "Avventura action retrò originale", true);
        } else if ("PROFILE".equals(state)) {
            drawMenu(g, "PROFILI", "N = nuovo profilo, Invio = seleziona", true);
            drawProfileStats(g);
        } else if ("PAUSED".equals(state)) {
            drawDim(g);
            drawMenu(g, "PAUSA", "Inventario: " + formatInventory(model.getPlayer().getInventory()), false);
        } else if ("DOOR_PROMPT".equals(state)) {
            drawDim(g);
            drawMenu(g, "PORTA CHIUSA", model.getMessage(), false);
        } else if ("DOOR_OPENING".equals(state)) {
            drawDim(g);
            drawCenteredTitle(g, "PORTA", model.getMessage());
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

    /**
     * Draws a compact dungeon minimap using DungeonLayout and DungeonProgress.
     * Only visited rooms are rendered and connections are shown only between visited rooms.
     * Door open/closed state is read from DungeonProgress to avoid duplicating state.
     */
    private void drawMinimap(Graphics2D g) {
        jzelda.levels.LevelManager lm = model.getLevelManager();
        DungeonLayout layout = lm.getCurrentDungeonLayout();
        if (layout == null) {
            return;
        }
        DungeonProgress progress = lm.getDungeonProgress();

        int rows = layout.getRows();
        int cols = layout.getColumns();

        final int cellSize = 18;
        final int gap = 4;
        final int padding = 6;

        int mapW = cols * cellSize + (cols - 1) * gap + padding * 2;
        int mapH = rows * cellSize + (rows - 1) * gap + padding * 2;

        int originX = GameConfig.WINDOW_WIDTH - mapW - 12;
        int originY = GameConfig.HUD_HEIGHT + 12;

        // background box
        drawBox(g, originX, originY, mapW, mapH);

        String currentRoomId = lm.getCurrentRoomId();

        java.util.Map<String, java.awt.Point> centers = new java.util.HashMap<>();

        // draw visited rooms and collect centers
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                java.util.Optional<String> maybe = layout.getRoomId(r, c);
                if (!maybe.isPresent()) {
                    continue;
                }
                String roomId = maybe.get();
                if (!progress.isRoomVisited(roomId)) {
                    continue; // do not reveal unvisited rooms
                }

                int x = originX + padding + c * (cellSize + gap);
                int y = originY + padding + r * (cellSize + gap);

                int cx = x + cellSize / 2;
                int cy = y + cellSize / 2;
                centers.put(roomId, new java.awt.Point(cx, cy));

                if (roomId.equals(currentRoomId)) {
                    g.setColor(RetroPalette.GOLD);
                    g.fillRect(x, y, cellSize, cellSize);
                } else {
                    g.setColor(RetroPalette.BLUE);
                    g.fillRect(x, y, cellSize, cellSize);
                }
                g.setColor(RetroPalette.INK);
                g.drawRect(x, y, cellSize, cellSize);
            }
        }

        // draw connections only between visited rooms
        for (java.util.Map.Entry<String, java.awt.Point> entry : centers.entrySet()) {
            String roomId = entry.getKey();
            java.awt.Point p = entry.getValue();
            java.util.Optional<jzelda.levels.RoomPosition> posOpt = layout.findRoom(roomId);
            if (!posOpt.isPresent()) {
                continue;
            }
            jzelda.levels.RoomPosition pos = posOpt.get();

            // check right and down neighbors to avoid duplicating edges
            jzelda.entities.Direction[] checks = new jzelda.entities.Direction[] { jzelda.entities.Direction.RIGHT,
                    jzelda.entities.Direction.DOWN };
            for (jzelda.entities.Direction dir : checks) {
                java.util.Optional<String> adjOpt = layout.getAdjacentRoom(pos.row(), pos.column(), dir);
                if (!adjOpt.isPresent()) {
                    continue;
                }
                String neighborId = adjOpt.get();
                if (!progress.isRoomVisited(neighborId)) {
                    continue;
                }
                java.awt.Point q = centers.get(neighborId);
                if (q == null) {
                    continue;
                }

                // determine if there is a door and whether it is open
                boolean hasDoor = false;
                boolean open = true;
                Level level = lm.getLevelById(roomId);
                if (level != null) {
                    for (LevelExit exit : level.getExits()) {
                        if (exit.getType() != ExitType.ROOM || exit.getDirection() != dir) {
                            continue;
                        }
                        if (exit.isInitiallyLocked()) {
                            hasDoor = true;
                            open = lm.getDungeonProgress().isDoorOpen(exit.getDoorStateId());
                        } else {
                            open = true;
                        }
                        break;
                    }
                }

                if (hasDoor && !open) {
                    g.setColor(Color.DARK_GRAY);
                    float[] dash = { 4f, 4f };
                    g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 0f));
                } else {
                    g.setColor(RetroPalette.GOLD);
                    g.setStroke(new BasicStroke(2));
                }
                g.drawLine(p.x, p.y, q.x, q.y);
                g.setStroke(new BasicStroke(1));
            }
        }
    }
}
