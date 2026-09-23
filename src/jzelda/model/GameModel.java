package jzelda.model;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jzelda.controller.commands.InputCommand;
import jzelda.entities.Direction;
import jzelda.entities.Enemy;
import jzelda.entities.Entity;
import jzelda.entities.Player;
import jzelda.entities.Projectile;
import jzelda.items.Item;
import jzelda.levels.DoorVisualState;
import jzelda.levels.ExitType;
import jzelda.levels.ItemDrop;
import jzelda.levels.Level;
import jzelda.levels.LevelExit;
import jzelda.levels.LevelFactory;
import jzelda.levels.LevelManager;
import jzelda.levels.RupeePickup;
import jzelda.patterns.AbstractObservableModel;
import jzelda.patterns.GameFacade;
import jzelda.persistence.Profile;
import jzelda.shop.Shop;
import jzelda.states.DoorOpeningState;
import jzelda.states.DoorPromptState;
import jzelda.states.GameOverState;
import jzelda.states.GameState;
import jzelda.states.LeaderboardState;
import jzelda.states.MenuState;
import jzelda.states.PausedState;
import jzelda.states.PlayingState;
import jzelda.states.ProfileSelectState;
import jzelda.states.ShopState;
import jzelda.states.VictoryState;

/**
 * Core game model in the MVC architecture. It owns the full game state: current
 * profile, levels, entities, score, lives, rupees, inventory, shop, leaderboard
 * and progression. The view observes it but does not change internal state
 * directly.
 */
public class GameModel extends AbstractObservableModel {
    private final GameFacade facade;
    private final Map<String, GameState> states = new HashMap<>();
    private final LevelManager levelManager;
    private final Shop shop = new Shop();
    private final List<VisualEffect> effects = new ArrayList<>();
    private GameState currentState;
    private Player player;
    private int score;
    private int continuesLeft = GameConfig.STARTING_CONTINUES;
    private int menuIndex;
    private List<String> menuOptions = new ArrayList<>();
    private String message = "";
    private boolean exitRequested;
    private boolean minimapVisible;
    private boolean pendingNextLevelAfterShop;
    private boolean runFinished;
    private boolean exitTransitionLocked;
    private long titleClockMs;
    private long attackFlashMs;
    private long attackAnimationDurationMs;
    private Direction attackDirection = Direction.DOWN;
    private static final long UNARMED_ATTACK_VISUAL_DURATION_MS = 300L;
    private Rectangle2D.Double lastAttackBounds;
    private String pendingDoorRoomId;
    private String pendingDoorExitId;
    private String pendingDoorStateId;
    private String openingDoorRoomId;
    private String openingDoorExitId;
    private long doorOpeningElapsedMs;
    private static final long DOOR_OPENING_DURATION_MS = 420L;

    /**
     * Builds the model and loads the level campaign.
     *
     * @param facade infrastructure facade for resources, audio and persistence
     */
    public GameModel(GameFacade facade) {
        this.facade = facade;
        LevelFactory levelFactory = new LevelFactory(facade.resources());
        this.levelManager = new LevelManager(levelFactory);
        this.player = new Player(levelManager.getCurrentLevel().getStartX(), levelManager.getCurrentLevel().getStartY());
        registerStates();
        changeState("MENU");
    }

    private void registerStates() {
        states.put("MENU", new MenuState());
        states.put("PROFILE", new ProfileSelectState());
        states.put("PLAYING", new PlayingState());
        states.put("SHOP", new ShopState());
        states.put("PAUSED", new PausedState());
        states.put("GAME_OVER", new GameOverState());
        states.put("LEADERBOARD", new LeaderboardState());
        states.put("VICTORY", new VictoryState());
        states.put("DOOR_PROMPT", new DoorPromptState());
        states.put("DOOR_OPENING", new DoorOpeningState());
    }

    /**
     * Advances the active state and notifies observers.
     *
     * @param deltaMs elapsed milliseconds
     */
    public void update(long deltaMs) {
        titleClockMs += Math.max(0, deltaMs);
        if (currentState != null) {
            currentState.update(this, deltaMs);
        }
        effects.forEach(effect -> effect.update(deltaMs));
        effects.removeIf(effect -> !effect.isAlive());
        attackFlashMs = Math.max(0, attackFlashMs - deltaMs);
        notifyObservers("TICK", message);
    }

    /**
     * Delegates a command to the current State object.
     *
     * @param command command to execute
     */
    public void handleCommand(InputCommand command) {
        if (command != null && currentState != null) {
            currentState.handleCommand(this, command);
            notifyObservers("COMMAND", command.getName());
        }
    }

    public void toggleMinimap() {
        minimapVisible = !minimapVisible;
        notifyObservers("MINIMAP", minimapVisible ? "OPEN" : "CLOSED");
    }

    public boolean isMinimapVisible() {
        return minimapVisible;
    }

    /**
     * Changes the active state and calls its enter hook.
     *
     * @param key state key
     */
    public void changeState(String key) {
        GameState next = states.get(key);
        if (next == null) {
            throw new IllegalArgumentException("Stato non registrato: " + key);
        }
        currentState = next;
        menuIndex = 0;
        next.enter(this);
        notifyObservers("STATE_CHANGED", next.getName());
    }

    /**
     * Starts a brand-new campaign with the selected profile.
     *
     * @param profile selected profile
     */
    public void startNewGame(Profile profile) {
        if (profile != null) {
            facade.profiles().selectByNickname(profile.getNickname());
        }
        score = 0;
        continuesLeft = GameConfig.STARTING_CONTINUES;
        pendingNextLevelAfterShop = false;
        runFinished = false;
        exitTransitionLocked = false;
        clearPendingDoor();
        openingDoorRoomId = null;
        openingDoorExitId = null;
        doorOpeningElapsedMs = 0L;
        levelManager.getDungeonProgress().reset();
        effects.clear();
        levelManager.reloadCampaign();
        player = new Player(levelManager.getCurrentLevel().getStartX(), levelManager.getCurrentLevel().getStartY());
        message = "Partita iniziata con " + facade.profiles().getCurrentProfile().getNickname();
        changeState("PLAYING");
    }

    /**
     * Creates and selects a new profile, then refreshes the profile menu.
     *
     * @param nickname profile nickname
     */
    public void createProfile(String nickname) {
        facade.profiles().createProfile(nickname, "foglia");
        changeState("PROFILE");
    }

    /**
     * Updates entities, collision, pickups, projectiles and level completion.
     *
     * @param deltaMs elapsed milliseconds
     */
    public void updateGameplay(long deltaMs) {
        player.updateTimers(deltaMs);
        Level level = levelManager.getCurrentLevel();
        level.activeEnemies().collect(Collectors.toList()).forEach(enemy -> enemy.update(this, deltaMs));
        updateProjectiles(level, deltaMs);
        collectPickups(level);
        checkExit(level);
    }

    private void updateProjectiles(Level level, long deltaMs) {
        for (Projectile projectile : level.getProjectiles()) {
            projectile.update(deltaMs);
            if (level.isBlocked(projectile.getBounds())) {
                projectile.setAlive(false);
            }
            if (projectile.isAlive() && projectile.getBounds().intersects(player.getCollisionBounds())) {
                projectile.setAlive(false);
                damagePlayer(projectile.getDamage(), "Proiettile nemico", directionFromProjectileToPlayer(projectile));
            }
        }
        level.removeInactiveProjectiles();
    }

    private void collectPickups(Level level) {
        Rectangle2D.Double playerBody = player.getCollisionBounds();
        List<RupeePickup> rupees = level.activeRupees().filter(rupee -> rupee.getBounds().intersects(playerBody))
                .collect(Collectors.toList());
        for (RupeePickup rupee : rupees) {
            rupee.collect();
            player.addRupees(rupee.getValue());
            addScore(rupee.getValue() * 10);
            addEffect("+" + rupee.getValue() + " rupie", rupee.getX(), rupee.getY());
            facade.audio().playEffect("rupee.wav");
        }
        List<ItemDrop> drops = level.activeItemDrops().filter(drop -> drop.getBounds().intersects(playerBody))
                .collect(Collectors.toList());
        for (ItemDrop drop : drops) {
            drop.collect();
            Item item = drop.getItem();
            item.apply(this);
            addScore(50);
            addEffect(item.getName(), drop.getX(), drop.getY());
        }
    }

    private void checkExit(Level level) {
        Rectangle2D.Double playerBounds = player.getCollisionBounds();

        if (exitTransitionLocked) {
            if (!isPlayerOverAnyExit(level, playerBounds)) {
                exitTransitionLocked = false;
                message = "";
            }
            return;
        }

        level.getExits().stream().filter(this::isExitOpen).filter(exit -> playerBounds.intersects(exit.getBounds())).filter(this::canUseExit).findFirst()
                .ifPresent(this::transitionThroughExit);
    }

    private boolean isExitOpen(LevelExit exit) {
        return !exit.isInitiallyLocked() || levelManager.getDungeonProgress().isDoorOpen(exit.getDoorStateId());
    }

    private boolean isPlayerOverAnyExit(Level level, Rectangle2D.Double playerBounds) {
        for (LevelExit exit : level.getExits()) {
            if (playerBounds.intersects(exit.getBounds())) {
                return true;
            }
        }
        return false;
    }

    private boolean canUseExit(LevelExit exit) {
        Level level = levelManager.getCurrentLevel();
        if (exit.isRequiresEnemiesDefeated() && level.activeEnemies().anyMatch(Enemy::isAlive)) {
            message = "Sconfiggi prima tutti i nemici: " + level.countActiveEnemies() + " rimasti.";
            return false;
        }
        if (exit.isRequiresKey() && !exit.isInitiallyLocked()
            && player.getInventory().getOrDefault("key", 0) <= 0) {
            message = "Serve una chiave antica per aprire l'uscita.";
            return false;
        }
        return true;
    }

    private void transitionThroughExit(LevelExit exit) {
        Level level = levelManager.getCurrentLevel();
        if (level.hasLegacyExits()) {
            if (exit.isRequiresKey() && !exit.isInitiallyLocked() && !player.consumeInventory("key", 1)) {
                message = "Serve una chiave antica per aprire l'uscita.";
                return;
            }
            completeLevel();
            return;
        }

        if (exit.isRequiresKey() && !exit.isInitiallyLocked() && !player.consumeInventory("key", 1)) {
            message = "Serve una chiave antica per aprire l'uscita.";
            return;
        }

        if (exit.getType() == ExitType.LEVEL_COMPLETE) {
            completeLevel();
            return;
        }

        String targetRoomId = resolveTargetRoomId(exit);
        if (targetRoomId == null) {
            return;
        }
        System.out.println("[TRANSITION] exit=" + exit.getId() + " direction=" + exit.getDirection() + " currentRoom="
                + levelManager.getCurrentRoomId() + " targetRoom=" + targetRoomId + " currentIndex=" + levelManager.getCurrentIndex());

        Level targetLevel = levelManager.getLevelById(targetRoomId);
        if (targetLevel == null) {
            throw new IllegalStateException(
                    "Exit '" + exit.getId() + "' non ha targetRoom valido: " + targetRoomId);
        }

        LevelExit targetExit = resolveTargetExit(exit, targetLevel);
        if (targetExit == null) {
            return;
        }

        if (!applyRoomTransitionIfAny(exit, targetRoomId)) {
            return;
        }
        levelManager.synchronizeRoomConnection(levelManager.getCurrentRoomId(), level, exit,
            targetRoomId, targetLevel, targetExit);
        if (!levelManager.setCurrentLevel(targetRoomId)) {
            message = "Transizione bloccata: impossibile attivare la stanza target '" + targetRoomId + "'.";
            return;
        }

        placePlayerFromTargetExit(targetLevel, targetExit);
        if (exit.getType() == ExitType.SHOP) {
            enterShop(false);
        } else {
            changeState("PLAYING");
        }
        exitTransitionLocked = true;
    }

    private String resolveTargetRoomId(LevelExit exit) {
        if (exit.getTargetLevelId() != null) {
            return exit.getTargetLevelId();
        }

        if (levelManager.getCurrentDungeonLayout() == null) {
            message = "Transizione verso stanza implicita non disponibile: dungeon layout assente.";
            return null;
        }

        Optional<String> adjacent = levelManager.resolveAdjacentRoom(exit.getDirection());
        if (adjacent.isEmpty()) {
            message = "Nessuna stanza raggiungibile in direzione " + exit.getDirection() + ".";
            return null;
        }
        return adjacent.get();
    }

    private LevelExit resolveTargetExit(LevelExit sourceExit, Level targetLevel) {
        if (sourceExit.getTargetExitId() != null) {
            return targetLevel.findExitById(sourceExit.getTargetExitId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Exit di destinazione '" + sourceExit.getTargetExitId() + "' non trovata in " + targetLevel.getLevelId()));
        }

        if (sourceExit.getType() != ExitType.ROOM) {
            message = "Transizione non valida: targetExit mancante.";
            return null;
        }

        Direction expectedDirection = sourceExit.getDirection().opposite();
        List<LevelExit> candidates = targetLevel.getExits().stream().filter(exit -> exit.getDirection() == expectedDirection)
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            message = "Transizione bloccata: nessuna exit target con direzione " + expectedDirection + " in "
                    + targetLevel.getLevelId();
            return null;
        }
        if (candidates.size() > 1) {
            message = "Transizione ambigua: più exit di destinazione con direzione " + expectedDirection + " in "
                    + targetLevel.getLevelId();
            return null;
        }
        return candidates.get(0);
    }

    private boolean applyRoomTransitionIfAny(LevelExit sourceExit, String targetRoomId) {
        if (sourceExit.getType() != ExitType.ROOM) {
            return true;
        }
        if (sourceExit.getTargetLevelId() != null) {
            return true;
        }
        if (levelManager.getCurrentDungeonLayout() == null) {
            return true;
        }

        Optional<String> adjacent = levelManager.resolveAdjacentRoom(sourceExit.getDirection());
        if (adjacent.isEmpty() || !targetRoomId.equals(adjacent.get())) {
            message = "Transizione geometrica incoerente con la topologia del dungeon.";
            return false;
        }
        return true;
    }

    private void placePlayerFromTargetExit(Level targetLevel, LevelExit targetExit) {
        double spawnX = targetExit.getBounds().x;
        double spawnY = targetExit.getBounds().y;
        switch (targetExit.getDirection()) {
        case UP:
            spawnX = targetExit.getBounds().x + (targetExit.getBounds().width - player.getWidth()) / 2.0;
            spawnY = targetExit.getBounds().getMaxY();
            break;
        case DOWN:
            spawnX = targetExit.getBounds().x + (targetExit.getBounds().width - player.getWidth()) / 2.0;
            spawnY = targetExit.getBounds().y - player.getHeight();
            break;
        case LEFT:
            spawnX = targetExit.getBounds().getMaxX();
            spawnY = targetExit.getBounds().y + (GameConfig.TILE_SIZE - player.getHeight()) / 2.0;
            break;
        case RIGHT:
            spawnX = targetExit.getBounds().x - player.getWidth();
            spawnY = targetExit.getBounds().y + (GameConfig.TILE_SIZE - player.getHeight()) / 2.0;
            break;
        default:
            break;
        }
        player.setPosition(spawnX, spawnY);
        player.restoreFullHealth();
    }

    /**
     * Moves the player while respecting map collisions.
     *
     * @param direction movement direction
     */
    public void movePlayer(Direction direction) {
        if (direction == null || direction == Direction.NONE || !"PLAYING".equals(getStateName())) {
            return;
        }
        player.setDirection(direction);
        double dx = direction.dx() * player.getSpeed();
        double dy = direction.dy() * player.getSpeed();
        moveEntityWithCollision(player, dx, dy);
    }

    /**
     * Moves an enemy and returns whether movement occurred.
     *
     * @param enemy enemy to move
     * @param dx horizontal delta
     * @param dy vertical delta
     * @return {@code true} if horizontal or vertical movement was accepted
     */
    public boolean moveEnemyWithCollision(Enemy enemy, double dx, double dy) {
        return moveEntityWithCollision(enemy, dx, dy);
    }

    private boolean moveEntityWithCollision(Entity entity, double dx, double dy) {
        Level level = levelManager.getCurrentLevel();
        boolean moved = false;

        // Horizontal attempt
        Rectangle2D.Double horizontal = entity.getCollisionBounds(entity.getX() + dx, entity.getY());
        boolean horizontalBlocked = level.isBlocked(horizontal);
        // check entity collisions: player cannot overlap alive enemies, enemies cannot overlap player
        if (!horizontalBlocked) {
            if (entity instanceof jzelda.entities.Player) {
                for (Enemy e : level.activeEnemies().filter(Enemy::isAlive).collect(Collectors.toList())) {
                    if (horizontal.intersects(e.getCollisionBounds())) {
                        horizontalBlocked = true;
                        break;
                    }
                }
            } else if (entity instanceof Enemy) {
                Player p = player;
                if (p != null && p.isAlive() && horizontal.intersects(p.getCollisionBounds())) {
                    horizontalBlocked = true;
                }
            }
        }
        if (!horizontalBlocked) {
            entity.translate(dx, 0);
            moved = Math.abs(dx) > 0.001;
        }

        // Vertical attempt
        Rectangle2D.Double vertical = entity.getCollisionBounds(entity.getX(), entity.getY() + dy);
        boolean verticalBlocked = level.isBlocked(vertical);
        if (!verticalBlocked) {
            if (entity instanceof jzelda.entities.Player) {
                for (Enemy e : level.activeEnemies().filter(Enemy::isAlive).collect(Collectors.toList())) {
                    if (vertical.intersects(e.getCollisionBounds())) {
                        verticalBlocked = true;
                        break;
                    }
                }
            } else if (entity instanceof Enemy) {
                Player p = player;
                if (p != null && p.isAlive() && vertical.intersects(p.getCollisionBounds())) {
                    verticalBlocked = true;
                }
            }
        }
        if (!verticalBlocked) {
            entity.translate(0, dy);
            moved = moved || Math.abs(dy) > 0.001;
        }

        return moved;
    }

    /**
     * Adds a projectile to the current level.
     *
     * @param projectile projectile to spawn
     */
    public void spawnProjectile(Projectile projectile) {
        levelManager.getCurrentLevel().addProjectile(projectile);
    }

    /**
 * Performs the current player attack. Before the sword is found this is a short
 * unarmed hit; after the sword pickup it becomes a longer and stronger sword
 * slash.
 */
public void attack() {
    if (!player.canAttack() || isAttackFlashVisible()) {
        return;
    }
    player.resetAttackCooldown();
    attackDirection = player.getDirection();
    lastAttackBounds = player.getAttackBounds();
    attackAnimationDurationMs = player.hasSwordEquipped() ? 260L : UNARMED_ATTACK_VISUAL_DURATION_MS;
    attackFlashMs = attackAnimationDurationMs;
    facade.audio().playEffect("attack.wav");

    int attackDamage = player.getAttackDamage();
    List<Enemy> hitEnemies = levelManager.getCurrentLevel().activeEnemies()
            .filter(enemy -> enemy.getBounds().intersects(lastAttackBounds)).collect(Collectors.toList());
    boolean hitAny = hitEnemies.stream().anyMatch(Enemy::isAlive);
    for (Enemy enemy : hitEnemies) {
        enemy.damage(attackDamage);
        addEffect(player.hasSwordEquipped() ? "slash" : "hit", enemy.getX(), enemy.getY());
        if (!enemy.isAlive()) {
            addScore(100);
            player.addRupees(1);
            addEffect("+1 rupia", enemy.getX(), enemy.getY() - 12);
        }
    }
    if (hitAny) {
        facade.audio().playEffect("hit.wav");
    }
}

    /**
     * Damages the player and transitions to game over when health reaches zero.
     *
     * @param amount damage amount
     * @param reason message shown in the HUD
     */
    public void damagePlayer(int amount, String reason) {
        damagePlayer(amount, reason, null);
    }

    /** Applies damage or consumes a frontal shield block. */
    public void damagePlayer(int amount, String reason, Direction sourceDirection) {
        if (player.isInvulnerable() || runFinished) {
            return;
        }
        if (player.parryHit(sourceDirection)) {
            player.grantInvulnerability(900);
            addEffect("parata", player.getX(), player.getY());
            message = "Colpo parato.";
            return;
        }
        int realDamage = amount;
        player.damage(realDamage);
        player.grantInvulnerability(900);
        addEffect("-" + realDamage, player.getX(), player.getY());
        facade.audio().playEffect("hit.wav");
        message = reason;
        if (player.getHealth() <= 0) {
            handlePlayerDeath();
        }
    }

    /** Returns the cardinal direction from the player toward an enemy. */
    public Direction directionFromPlayerTo(Entity source) {
        double dx = source.getBounds().getCenterX() - player.getBounds().getCenterX();
        double dy = source.getBounds().getCenterY() - player.getBounds().getCenterY();
        return Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? Direction.LEFT : Direction.RIGHT)
                : (dy < 0 ? Direction.UP : Direction.DOWN);
    }

    private Direction directionFromProjectileToPlayer(Projectile projectile) {
        double dx = projectile.getVelocityX();
        double dy = projectile.getVelocityY();
        Direction travel = Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? Direction.LEFT : Direction.RIGHT)
                : (dy < 0 ? Direction.UP : Direction.DOWN);
        return travel.opposite();
    }

    private void handlePlayerDeath() {
        facade.audio().playEffect("gameover.wav");
        player.loseLife();
        if (player.getLives() == 0 && continuesLeft == 0) {
            finishRun(false);
        }
        changeState("GAME_OVER");
    }

    /**
     * Continues after defeat if lives or continues remain.
     */
    public void continueRun() {
        if (player.getLives() > 0) {
            levelManager.reloadCurrentLevel();
            placePlayerAtLevelStart();
            player.restoreFullHealth();
            exitTransitionLocked = false;
            message = "Continua: vite rimaste " + player.getLives();
            changeState("PLAYING");
            return;
        }
        if (continuesLeft > 0) {
            continuesLeft--;
            player.setLives(GameConfig.STARTING_LIVES);
            levelManager.reloadCurrentLevel();
            placePlayerAtLevelStart();
            player.restoreFullHealth();
            exitTransitionLocked = false;
            message = "Continua usato. Continua rimasti: " + continuesLeft;
            changeState("PLAYING");
            return;
        }
        finishRun(false);
        changeState("MENU");
    }

    /**
     * Completes the active level and advances progression.
     */
    public void completeLevel() {
        Level level = levelManager.getCurrentLevel();
        if (level.isCompleted()) {
            return;
        }
        level.markCompleted();
        addScore(500 + (int) (level.countActiveEnemies() * 10));
        addEffect("Livello completato", player.getX(), player.getY() - 20);
        facade.audio().playEffect("levelcomplete.wav");
        if (levelManager.isLastLevel()) {
            finishRun(true);
            changeState("VICTORY");
        } else if (levelManager.getCurrentNumber() == 4) {
            enterShop(true);
        } else {
            levelManager.nextLevel();
            placePlayerAtLevelStart();
            message = "Nuovo livello: " + levelManager.getCurrentLevel().getName();
            changeState("PLAYING");
        }
    }

    /**
     * Enters the separate shop state.
     *
     * @param advanceAfterShop whether leaving the shop should advance to the next
     *                         level
     */
    public void enterShop(boolean advanceAfterShop) {
        pendingNextLevelAfterShop = advanceAfterShop;
        changeState("SHOP");
    }

    /**
     * Leaves the shop and returns to play, optionally advancing the level.
     */
    public void leaveShop() {
        if (pendingNextLevelAfterShop) {
            pendingNextLevelAfterShop = false;
            levelManager.nextLevel();
            placePlayerAtLevelStart();
            message = "Uscito dalla bottega. Prossimo livello: " + levelManager.getCurrentLevel().getName();
        } else {
            message = "Ritorno all'avventura.";
        }
        changeState("PLAYING");
    }

    /** Toggles pause through the State pattern. */
    public void pause() {
        if ("PLAYING".equals(getStateName())) {
            changeState("PAUSED");
        } else if ("PAUSED".equals(getStateName())) {
            changeState("PLAYING");
        }
    }

    /** Contextual interaction. Currently it rechecks exits and shop hints. */
    public void interact() {
        if ("PLAYING".equals(getStateName())) {
            Rectangle2D.Double interactionBounds = player.getInteractionBounds();
            Level level = levelManager.getCurrentLevel();
            for (LevelExit exit : level.getExits()) {
                if (exit.isInitiallyLocked() && !isExitOpen(exit)
                        && exit.getDirection() == player.getDirection()
                        && interactionBounds.intersects(exit.getBounds())) {
                    if (player.getInventory().getOrDefault("key", 0) <= 0) {
                        message = "La porta è chiusa. Torna con una chiave.";
                    } else {
                        pendingDoorRoomId = levelManager.getCurrentRoomId();
                        pendingDoorExitId = exit.getId();
                        pendingDoorStateId = exit.getDoorStateId();
                        changeState("DOOR_PROMPT");
                    }
                    return;
                }
            }
        }
    }

    /** Confirms the pending door prompt. */
    public void confirmDoorPrompt() {
        if (!"DOOR_PROMPT".equals(getStateName()) || menuIndex != 0) {
            clearPendingDoor();
            changeState("PLAYING");
            return;
        }
        Level level = levelManager.getCurrentLevel();
        LevelExit exit = level.findExitById(pendingDoorExitId).orElse(null);
        if (exit == null || !exit.isInitiallyLocked() || isExitOpen(exit)
                || !pendingDoorStateId.equals(exit.getDoorStateId())
                || player.getInventory().getOrDefault("key", 0) <= 0) {
            clearPendingDoor();
            changeState("PLAYING");
            return;
        }
        player.consumeInventory("key", 1);
        openingDoorRoomId = pendingDoorRoomId;
        openingDoorExitId = pendingDoorExitId;
        doorOpeningElapsedMs = 0L;
        clearPendingDoor();
        changeState("DOOR_OPENING");
    }

    /** Cancels the pending door prompt without consuming a key. */
    public void cancelDoorPrompt() {
        clearPendingDoor();
        changeState("PLAYING");
    }

    /** Advances the non-blocking door opening animation. */
    public void updateDoorOpening(long deltaMs) {
        doorOpeningElapsedMs += Math.max(0L, deltaMs);
        if (doorOpeningElapsedMs >= DOOR_OPENING_DURATION_MS) {
            Level currentLevel = levelManager.getCurrentLevel();
            LevelExit openingExit = currentLevel.findExitById(openingDoorExitId).orElse(null);
            String targetRoomId = openingExit == null ? null : resolveTargetRoomId(openingExit);
            if (openingExit != null && targetRoomId != null) {
                Level targetLevel = levelManager.getLevelById(targetRoomId);
                LevelExit targetExit = targetLevel == null ? null : resolveTargetExit(openingExit, targetLevel);
                levelManager.synchronizeRoomConnection(openingDoorRoomId, currentLevel, openingExit,
                    targetRoomId, targetLevel, targetExit);
            }
            Level level = levelManager.getCurrentLevel();
            level.applyDungeonProgress(levelManager.getDungeonProgress());
            openingDoorRoomId = null;
            openingDoorExitId = null;
            doorOpeningElapsedMs = 0L;
            changeState("PLAYING");
        }
    }

    private String findOpeningDoorStateId() {
        Level level = levelManager.getCurrentLevel();
        LevelExit exit = level.findExitById(openingDoorExitId).orElse(null);
        return exit == null ? null : exit.getDoorStateId();
    }

    /** @return the runtime sprite name for the currently displayed room */
    public String getCurrentRoomSpriteName() {
        return levelManager.getDungeonProgress().getRoomSpriteState(levelManager.getCurrentRoomId());
    }

    private void clearPendingDoor() {
        pendingDoorRoomId = null;
        pendingDoorExitId = null;
        pendingDoorStateId = null;
    }

    /**
     * Finishes the run and writes profile and leaderboard data exactly once.
     *
     * @param victory whether the game was won
     */
    public void finishRun(boolean victory) {
        if (runFinished) {
            return;
        }
        runFinished = true;
        Profile profile = facade.profiles().getCurrentProfile();
        profile.recordResult(victory, score);
        facade.profiles().save();
        facade.leaderboard().addEntry(profile.getNickname(), score, levelManager.getCurrentNumber(), victory);
        facade.audio().playEffect(victory ? "victory.wav" : "gameover.wav");
    }

    /**
     * Adds score points.
     *
     * @param amount points to add
     */
    public void addScore(int amount) {
        score += Math.max(0, amount);
    }

    /**
     * Sets menu options for the current state.
     *
     * @param options option labels
     */
    public void setMenuOptions(List<String> options) {
        menuOptions = new ArrayList<>(options == null ? Collections.emptyList() : options);
        if (menuIndex >= menuOptions.size()) {
            menuIndex = 0;
        }
    }

    /**
     * Moves the menu cursor.
     *
     * @param delta signed menu movement
     */
    public void navigateMenu(int delta) {
        if (menuOptions.isEmpty()) {
            menuIndex = 0;
            return;
        }
        menuIndex = (menuIndex + delta) % menuOptions.size();
        if (menuIndex < 0) {
            menuIndex += menuOptions.size();
        }
    }

    /** Placeholder used by generic confirm commands; states usually handle it. */
    public void confirmSelection() {
        // Handled by concrete states.
    }

    /** Default back behavior used by commands when a state does not override it. */
    public void goBack() {
        changeState("MENU");
    }

    /**
     * Handles numbered choices, currently used for the shop.
     *
     * @param number selected number
     */
    public void selectNumber(int number) {
        if ("SHOP".equals(getStateName())) {
            boolean bought = shop.purchase(this, number).isPresent();
            if (bought) {
                message = "Acquisto completato.";
            } else {
                message = "Rupie insufficienti o scelta non valida.";
            }
        }
    }

    /**
     * Adds a transient visual effect.
     *
     * @param text effect text
     * @param x x coordinate
     * @param y y coordinate
     */
    public void addEffect(String text, double x, double y) {
        effects.add(new VisualEffect(text, x, y, 900));
    }

    /**
     * Advances a title/victory animation counter.
     *
     * @param deltaMs elapsed milliseconds
     */
    public void tickTitleAnimation(long deltaMs) {
        titleClockMs += Math.max(0, deltaMs);
    }

    /** Requests that the controller close the window. */
    public void requestExit() {
        exitRequested = true;
    }

    private void placePlayerAtLevelStart() {
        Level level = levelManager.getCurrentLevel();
        player.setPosition(level.getStartX(), level.getStartY());
        exitTransitionLocked = false;
        player.restoreFullHealth();
    }

    /**
     * @return infrastructure facade
     */
    public GameFacade getFacade() {
        return facade;
    }

    /**
     * @return current player object
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * @return level progression manager
     */
    public LevelManager getLevelManager() {
        return levelManager;
    }

    /**
     * @return current level
     */
    public Level getCurrentLevel() {
        return levelManager.getCurrentLevel();
    }

    /**
     * @return shop model
     */
    public Shop getShop() {
        return shop;
    }

    /**
     * @return current score
     */
    public int getScore() {
        return score;
    }

    /**
     * @return remaining continues
     */
    public int getContinuesLeft() {
        return continuesLeft;
    }

    /**
     * @return selected menu index
     */
    public int getMenuIndex() {
        return menuIndex;
    }

    /**
     * @return menu option labels
     */
    public List<String> getMenuOptions() {
        return new ArrayList<>(menuOptions);
    }

    /**
     * @return latest HUD/status message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the status message.
     *
     * @param message message to display
     */
    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }

    /**
     * @return current state display name
     */
    public String getStateName() {
        return currentState == null ? "NONE" : currentState.getName();
    }

    /**
     * @return live visual effects
     */
    public List<VisualEffect> getEffects() {
        return new ArrayList<>(effects);
    }

    /**
     * @return title animation clock used for visual effects
     */
    public long getTitleClockMs() {
        return titleClockMs;
    }

    /** @return true while a door opening animation is active */
    public boolean isDoorOpening() {
        return "DOOR_OPENING".equals(getStateName());
    }

    public String getOpeningDoorRoomId() {
        return openingDoorRoomId;
    }

    public String getOpeningDoorExitId() {
        return openingDoorExitId;
    }

    public long getDoorOpeningElapsedMs() {
        return doorOpeningElapsedMs;
    }

    public int getDoorOpeningFrame() {
        return (int) Math.min(2L, doorOpeningElapsedMs / 140L);
    }

    /** Returns the visual state for a room exit, including shared progress. */
    public DoorVisualState getDoorVisualState(LevelExit exit) {
        if (exit == null || !exit.isInitiallyLocked()) {
            return DoorVisualState.OPEN;
        }
        if (isDoorOpening() && exit.getId().equals(openingDoorExitId)
                && levelManager.getCurrentRoomId().equals(openingDoorRoomId)) {
            return DoorVisualState.OPENING;
        }
        return levelManager.getDungeonProgress().isDoorOpen(exit.getDoorStateId())
                ? DoorVisualState.OPEN : DoorVisualState.CLOSED;
    }

    
    /**
     * @return elapsed milliseconds since the current attack animation started
     */
    public long getAttackAnimationElapsedMs() {
        if (!isAttackFlashVisible()) {
            return 0L;
        }
        return Math.max(0L, attackAnimationDurationMs - attackFlashMs);
    }

    /** @return total visual duration of the current attack */
    public long getAttackAnimationDurationMs() {
        return attackAnimationDurationMs;
    }

    /** @return direction captured when the current attack started */
    public Direction getAttackDirection() {
        return attackDirection;
    }

    /**
     * @return {@code true} when an attack rectangle should be drawn
     */
    public boolean isAttackFlashVisible() {
        return attackFlashMs > 0 && lastAttackBounds != null;
    }

    /**
     * @return last attack rectangle
     */
    public Rectangle2D.Double getLastAttackBounds() {
        return lastAttackBounds;
    }

    /**
     * @return {@code true} when controller should close the window
     */
    public boolean isExitRequested() {
        return exitRequested;
    }
}
