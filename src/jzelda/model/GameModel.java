package jzelda.model;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jzelda.controller.commands.InputCommand;
import jzelda.entities.Direction;
import jzelda.entities.Enemy;
import jzelda.entities.Entity;
import jzelda.entities.Player;
import jzelda.entities.Projectile;
import jzelda.items.Item;
import jzelda.levels.ItemDrop;
import jzelda.levels.Level;
import jzelda.levels.LevelFactory;
import jzelda.levels.LevelManager;
import jzelda.levels.RupeePickup;
import jzelda.patterns.AbstractObservableModel;
import jzelda.patterns.GameFacade;
import jzelda.persistence.Profile;
import jzelda.shop.Shop;
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
    private boolean pendingNextLevelAfterShop;
    private boolean runFinished;
    private long titleClockMs;
    private long attackFlashMs;
    private Rectangle2D.Double lastAttackBounds;

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
            if (projectile.isAlive() && projectile.getBounds().intersects(player.getBounds())) {
                projectile.setAlive(false);
                damagePlayer(projectile.getDamage(), "Proiettile nemico");
            }
        }
        level.removeInactiveProjectiles();
    }

    private void collectPickups(Level level) {
        List<RupeePickup> rupees = level.activeRupees().filter(rupee -> rupee.getBounds().intersects(player.getBounds()))
                .collect(Collectors.toList());
        for (RupeePickup rupee : rupees) {
            rupee.collect();
            player.addRupees(rupee.getValue());
            addScore(rupee.getValue() * 10);
            addEffect("+" + rupee.getValue() + " rupie", rupee.getX(), rupee.getY());
            facade.audio().playEffect("rupee.wav");
        }
        List<ItemDrop> drops = level.activeItemDrops().filter(drop -> drop.getBounds().intersects(player.getBounds()))
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
        if (!player.getBounds().intersects(level.getExitBounds())) {
            return;
        }
        if (!level.allEnemiesDefeated()) {
            message = "Sconfiggi prima tutti i nemici: " + level.countActiveEnemies() + " rimasti.";
            return;
        }
        if (level.requiresKey() && !player.consumeInventory("key", 1)) {
            message = "Serve una chiave antica per aprire l'uscita.";
            return;
        }
        completeLevel();
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
        Rectangle2D.Double horizontal = new Rectangle2D.Double(entity.getX() + dx, entity.getY(), entity.getWidth(),
                entity.getHeight());
        if (!level.isBlocked(horizontal)) {
            entity.translate(dx, 0);
            moved = Math.abs(dx) > 0.001;
        }
        Rectangle2D.Double vertical = new Rectangle2D.Double(entity.getX(), entity.getY() + dy, entity.getWidth(),
                entity.getHeight());
        if (!level.isBlocked(vertical)) {
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
     * Performs a sword attack against enemies in front of the player.
     */
    public void attack() {
        if (!player.canAttack()) {
            return;
        }
        player.resetAttackCooldown();
        lastAttackBounds = player.getAttackBounds();
        attackFlashMs = 120;
        facade.audio().playEffect("attack.wav");
        List<Enemy> hitEnemies = levelManager.getCurrentLevel().activeEnemies()
                .filter(enemy -> enemy.getBounds().intersects(lastAttackBounds)).collect(Collectors.toList());
        boolean hitAny = hitEnemies.stream().anyMatch(Enemy::isAlive);
        for (Enemy enemy : hitEnemies) {
            enemy.damage(1);
            addEffect("hit", enemy.getX(), enemy.getY());
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
        if (player.isInvulnerable() || runFinished) {
            return;
        }
        int realDamage = player.hasShield() ? Math.max(1, amount - 1) : amount;
        player.damage(realDamage);
        player.grantInvulnerability(900);
        addEffect("-" + realDamage, player.getX(), player.getY());
        facade.audio().playEffect("hit.wav");
        message = reason;
        if (player.getHealth() <= 0) {
            handlePlayerDeath();
        }
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
            checkExit(levelManager.getCurrentLevel());
        }
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
     * @return title animation clock
     */
    public long getTitleClockMs() {
        return titleClockMs;
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
