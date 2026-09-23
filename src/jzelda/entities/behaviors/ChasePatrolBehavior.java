package jzelda.entities.behaviors;

import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import jzelda.entities.Direction;
import jzelda.entities.Enemy;
import jzelda.entities.Player;
import jzelda.model.GameConfig;
import jzelda.model.GameModel;

/**
 * Chase + Patrol behavior for the Reaper.
 * Implements BFS pathfinding for detours and robust melee auto-attack.
 */
public class ChasePatrolBehavior implements EnemyBehavior {
    private static final int PATROL_RANGE_TILES = 3;
    private static final int CHASE_RANGE_X = GameConfig.TILE_SIZE * 8; // 8 tiles
    private static final int CHASE_RANGE_Y = GameConfig.TILE_SIZE * 7; // 7 tiles
    private static final double SPEED = 1.7;
    private static final double PATH_SPEED_MULTIPLIER = 1.20;
    private static final double AXIS_SWITCH_THRESHOLD = GameConfig.TILE_SIZE / 2.0;
    private static final double WAYPOINT_REACHED_DISTANCE = 4.0;
    private static final long STUCK_REPLAN_MS = 250L;

    private Double spawnX = null;
    private int patrolDirection = 1; // 1 = right, -1 = left

    private enum ChaseAxis { HORIZONTAL, VERTICAL }
    private ChaseAxis preferredAxis = null; // persistent while in chase

    private enum BehaviorMode { PATROL, CHASE, PATH, ATTACK_RECOVERY }
    private BehaviorMode mode = BehaviorMode.PATROL;

    // PATH state
    private static final class GridCell {
        final int row; final int column;
        GridCell(int r, int c) { row = r; column = c; }
    }
    private List<GridCell> currentPath = null;
    private int currentPathIndex = 0;
    private int pathTargetPlayerRow = -1;
    private int pathTargetPlayerColumn = -1;
    private Direction pathDirection = Direction.NONE;
    private long stuckClockMs = 0L;

    // Attack recovery state
    private boolean wasAttacking = false;
    private Direction recoveryFacing = null;

    @Override
    public void update(Enemy enemy, GameModel model, long deltaMs) {
        Player player = model.getPlayer();
        if (player == null) return;
        if (spawnX == null) spawnX = enemy.getX();

        // 1. ATTACKING
        if (enemy.isAttacking()) {
            enemy.setMoving(false);
            // robust impact check
            if (enemy.getAttackFrame() >= Enemy.ATTACK_IMPACT_FRAME && !enemy.isAttackDamageApplied()) {
                enemy.applyMeleeDamageIfNeeded(model);
            }
            return;
        }

        // remember transition to recovery
        if (wasAttacking && !enemy.isAttacking() && enemy.getAttackCooldownMs() > 0) {
            mode = BehaviorMode.ATTACK_RECOVERY;
            recoveryFacing = enemy.getDirection();
            wasAttacking = false;
        }

        // capture if started attacking this frame
        if (!enemy.isAttacking() && enemy.getAttackFrame() > 0) {
            // noop - defensive
        }

        // 2. ATTACK_RECOVERY handling
        Rectangle2D.Double body = enemy.getCollisionBounds();
        Rectangle2D.Double playerBody = player.getCollisionBounds();
        if (mode == BehaviorMode.ATTACK_RECOVERY) {
            Direction meleeDir = findMeleeDirection(enemy, player);
            if (meleeDir != Direction.NONE) {
                // keep facing and do not chase or path
                enemy.setMoving(false);
                enemy.setDirection(recoveryFacing != null ? recoveryFacing : enemy.getDirection());
                // if cooldown finished, restart attack towards meleeDir
                if (enemy.getAttackCooldownMs() == 0) {
                    enemy.setDirection(meleeDir);
                    recoveryFacing = meleeDir;
                    clearPath();
                    enemy.startAttack();
                    return;
                }
                return;
            } else {
                // player moved away -> exit recovery
                mode = BehaviorMode.CHASE;
                recoveryFacing = null;
            }
        }

        // 3. NEW ATTACK BEFORE ANY CHASE
        Direction meleeDirNow = findMeleeDirection(enemy, player);
        if (meleeDirNow != Direction.NONE && enemy.getAttackCooldownMs() == 0) {
            clearPath();
            enemy.setMoving(false);
            enemy.setDirection(meleeDirNow);
            recoveryFacing = meleeDirNow;
            enemy.startAttack();
            return;
        }

        // 4. CHECK CHASE RANGE
        double deltaX = player.getBounds().getCenterX() - enemy.getBounds().getCenterX();
        double deltaY = player.getBounds().getCenterY() - enemy.getBounds().getCenterY();
        boolean playerInRange = Math.abs(deltaX) <= CHASE_RANGE_X && Math.abs(deltaY) <= CHASE_RANGE_Y;
        if (!playerInRange) {
            clearPath();
            mode = BehaviorMode.PATROL;
            preferredAxis = null;
            recoveryFacing = null;
            doPatrol(enemy, model);
            return;
        }

        // 5. PATH FOLLOW if exists
        if (mode == BehaviorMode.PATH && currentPath != null && currentPathIndex < currentPath.size()) {
            // if player changed cell, invalidate and replan
            int playerCellCol = GameConfig.toTileColumn(playerBody.getCenterX());
            int playerCellRow = GameConfig.toTileRow(playerBody.getCenterY());
            if (playerCellRow != pathTargetPlayerRow || playerCellCol != pathTargetPlayerColumn) {
                clearPath();
                // allow fallthrough to chase/planning below
            } else {
                boolean cont = followCurrentPath(enemy, model, deltaMs);
                enemy.setMoving(cont);
                return;
            }
        }

        // 6. CHASE DIRECTO
        // choose preferred axis with hysteresis
        if (preferredAxis == null) preferredAxis = Math.abs(deltaX) > Math.abs(deltaY) ? ChaseAxis.HORIZONTAL : ChaseAxis.VERTICAL;
        else {
            if (preferredAxis == ChaseAxis.HORIZONTAL && Math.abs(deltaY) > Math.abs(deltaX) + AXIS_SWITCH_THRESHOLD) preferredAxis = ChaseAxis.VERTICAL;
            else if (preferredAxis == ChaseAxis.VERTICAL && Math.abs(deltaX) > Math.abs(deltaY) + AXIS_SWITCH_THRESHOLD) preferredAxis = ChaseAxis.HORIZONTAL;
        }

        // attempt direct movement
        boolean moved = false;
        if (preferredAxis == ChaseAxis.HORIZONTAL) {
            double dx = Math.signum(deltaX) * SPEED;
            moved = model.moveEnemyWithCollision(enemy, dx, 0);
            if (moved) {
                enemy.setDirection(dx < 0 ? Direction.LEFT : Direction.RIGHT);
                stuckClockMs = 0L;
                enemy.setMoving(true);
                return;
            }
            double dy = Math.signum(deltaY) * SPEED;
            if (Math.abs(dy) > 0) {
                moved = model.moveEnemyWithCollision(enemy, 0, dy);
                if (moved) {
                    enemy.setDirection(dy < 0 ? Direction.UP : Direction.DOWN);
                    stuckClockMs = 0L;
                    enemy.setMoving(true);
                    return;
                }
            }
        } else {
            double dy = Math.signum(deltaY) * SPEED;
            moved = model.moveEnemyWithCollision(enemy, 0, dy);
            if (moved) {
                enemy.setDirection(dy < 0 ? Direction.UP : Direction.DOWN);
                stuckClockMs = 0L;
                enemy.setMoving(true);
                return;
            }
            double dx = Math.signum(deltaX) * SPEED;
            if (Math.abs(dx) > 0) {
                moved = model.moveEnemyWithCollision(enemy, dx, 0);
                if (moved) {
                    enemy.setDirection(dx < 0 ? Direction.LEFT : Direction.RIGHT);
                    stuckClockMs = 0L;
                    enemy.setMoving(true);
                    return;
                }
            }
        }

        // 7. if chase blocked -> plan BFS
        // compute player cell and start cell
        int playerCol = GameConfig.toTileColumn(playerBody.getCenterX());
        int playerRow = GameConfig.toTileRow(playerBody.getCenterY());
        int startCol = GameConfig.toTileColumn(body.getCenterX());
        int startRow = GameConfig.toTileRow(body.getCenterY());

        // plan only if player cell changed or no existing path
        if (currentPath == null || pathTargetPlayerRow != playerRow || pathTargetPlayerColumn != playerCol) {
            planPath(enemy, model, startRow, startCol, playerRow, playerCol);
        }
        if (currentPath != null && !currentPath.isEmpty()) {
            mode = BehaviorMode.PATH;
            boolean cont = followCurrentPath(enemy, model, deltaMs);
            enemy.setMoving(cont);
            return;
        }

        // fallback: blocked and no path -> stand still
        enemy.setMoving(false);
    }

    private void doPatrol(Enemy enemy, GameModel model) {
        double leftLimit = spawnX - PATROL_RANGE_TILES * GameConfig.TILE_SIZE;
        double rightLimit = spawnX + PATROL_RANGE_TILES * GameConfig.TILE_SIZE;
        double nextX = enemy.getX() + patrolDirection * SPEED;
        if (nextX < leftLimit) patrolDirection = 1; else if (nextX > rightLimit) patrolDirection = -1;
        boolean moved = model.moveEnemyWithCollision(enemy, patrolDirection * SPEED, 0);
        enemy.setMoving(moved);
        if (moved) enemy.setDirection(patrolDirection < 0 ? Direction.LEFT : Direction.RIGHT);
        else patrolDirection *= -1;
    }

    /** Clears current path state */
    private void clearPath() {
        currentPath = null;
        currentPathIndex = 0;
        pathTargetPlayerRow = -1;
        pathTargetPlayerColumn = -1;
        pathDirection = Direction.NONE;
        stuckClockMs = 0L;
        mode = BehaviorMode.CHASE;
    }

    /** Helper to find melee direction independent from enemy direction */
    private Direction findMeleeDirection(Enemy enemy, Player player) {
        Rectangle2D.Double body = enemy.getCollisionBounds();
        Rectangle2D.Double playerBody = player.getCollisionBounds();
        Rectangle2D.Double left = new Rectangle2D.Double(body.x - GameConfig.TILE_SIZE, body.y, GameConfig.TILE_SIZE, body.height);
        Rectangle2D.Double right = new Rectangle2D.Double(body.getMaxX(), body.y, GameConfig.TILE_SIZE, body.height);
        Rectangle2D.Double up = new Rectangle2D.Double(body.x, body.y - GameConfig.TILE_SIZE, body.width, GameConfig.TILE_SIZE);
        Rectangle2D.Double down = new Rectangle2D.Double(body.x, body.getMaxY(), body.width, GameConfig.TILE_SIZE);
        if (left.intersects(playerBody)) return Direction.LEFT;
        if (right.intersects(playerBody)) return Direction.RIGHT;
        if (up.intersects(playerBody)) return Direction.UP;
        if (down.intersects(playerBody)) return Direction.DOWN;
        return Direction.NONE;
    }

    /** Plans a BFS path from start cell to one of four adjacent target cells around player */
    private void planPath(Enemy enemy, GameModel model, int startRow, int startCol, int playerRow, int playerCol) {
        LevelBoundChecker lb = new LevelBoundChecker(model, enemy);
        // target cells: up, down, left, right of player
        List<GridCell> targets = new ArrayList<>();
        if (isCellTraversable(lb, playerRow - 1, playerCol)) targets.add(new GridCell(playerRow - 1, playerCol));
        if (isCellTraversable(lb, playerRow + 1, playerCol)) targets.add(new GridCell(playerRow + 1, playerCol));
        if (isCellTraversable(lb, playerRow, playerCol - 1)) targets.add(new GridCell(playerRow, playerCol - 1));
        if (isCellTraversable(lb, playerRow, playerCol + 1)) targets.add(new GridCell(playerRow, playerCol + 1));

        currentPath = null;
        currentPathIndex = 0;
        pathTargetPlayerRow = playerRow;
        pathTargetPlayerColumn = playerCol;
        pathDirection = Direction.NONE;
        stuckClockMs = 0L;

        if (targets.isEmpty()) return; // no reachable adjacent

        boolean[][] visited = new boolean[GameConfig.MAP_ROWS][GameConfig.MAP_COLUMNS];
        GridCell[][] parent = new GridCell[GameConfig.MAP_ROWS][GameConfig.MAP_COLUMNS];
        ArrayDeque<GridCell> queue = new ArrayDeque<>();
        GridCell start = new GridCell(startRow, startCol);
        queue.add(start);
        visited[start.row][start.column] = true;

        GridCell found = null;
        while (!queue.isEmpty()) {
            GridCell cur = queue.removeFirst();
            // check if cur is one of targets
            for (GridCell t : targets) {
                if (t.row == cur.row && t.column == cur.column) { found = cur; break; }
            }
            if (found != null) break;
            // neighbors in deterministic order: UP, DOWN, LEFT, RIGHT
            int[][] dirs = { {-1,0}, {1,0}, {0,-1}, {0,1} };
            for (int[] d : dirs) {
                int nr = cur.row + d[0];
                int nc = cur.column + d[1];
                if (nr < 0 || nr >= GameConfig.MAP_ROWS || nc < 0 || nc >= GameConfig.MAP_COLUMNS) continue;
                if (visited[nr][nc]) continue;
                if (!isCellTraversable(lb, nr, nc)) continue;
                visited[nr][nc] = true;
                parent[nr][nc] = cur;
                queue.addLast(new GridCell(nr, nc));
            }
        }

        if (found == null) {
            // no path
            currentPath = null;
            return;
        }

        // reconstruct path from start->found
        List<GridCell> rev = new ArrayList<>();
        GridCell cur = found;
        while (cur != null) {
            rev.add(cur);
            cur = parent[cur.row][cur.column];
        }
        // rev contains found ... start. reverse
        List<GridCell> path = new ArrayList<>();
        for (int i = rev.size() - 1; i >= 0; i--) path.add(rev.get(i));
        // remove start cell from path waypoints
        if (!path.isEmpty() && path.get(0).row == start.row && path.get(0).column == start.column) {
            path.remove(0);
        }
        if (path.isEmpty()) {
            currentPath = null;
            return;
        }
        currentPath = path;
        currentPathIndex = 0;
        // pathTargetPlayerRow/Column already set
    }

    /** Checks whether a grid cell is traversable for the enemy */
    private static final class LevelBoundChecker {
        private final GameModel model;
        private final Enemy enemy;
        LevelBoundChecker(GameModel model, Enemy enemy) { this.model = model; this.enemy = enemy; }
    }

    private boolean isCellTraversable(LevelBoundChecker lb, int row, int column) {
        if (row < 0 || row >= GameConfig.MAP_ROWS || column < 0 || column >= GameConfig.MAP_COLUMNS) return false;
        double x = GameConfig.toPlayX(column) + (GameConfig.TILE_SIZE - lb.enemy.getWidth()) / 2.0;
        double y = GameConfig.toPlayY(row) + (GameConfig.TILE_SIZE - lb.enemy.getHeight()) / 2.0;
        Rectangle2D.Double candidate = lb.enemy.getCollisionBounds(x, y);
        return !lb.model.getCurrentLevel().isBlocked(candidate);
    }

    /** Follows currentPath waypoints; returns true if movement occurred */
    private boolean followCurrentPath(Enemy enemy, GameModel model, long deltaMs) {
        if (currentPath == null || currentPathIndex >= currentPath.size()) return false;
        GridCell cell = currentPath.get(currentPathIndex);
        double targetX = GameConfig.toPlayX(cell.column) + (GameConfig.TILE_SIZE - enemy.getWidth()) / 2.0;
        double targetY = GameConfig.toPlayY(cell.row) + (GameConfig.TILE_SIZE - enemy.getHeight()) / 2.0;
        double dx = targetX - enemy.getX();
        double dy = targetY - enemy.getY();
        // determine movement axis and direction for this waypoint once
        if (pathDirection == Direction.NONE) {
            if (cell.column > GameConfig.toTileColumn(enemy.getCollisionBounds().getCenterX())) pathDirection = Direction.RIGHT;
            else if (cell.column < GameConfig.toTileColumn(enemy.getCollisionBounds().getCenterX())) pathDirection = Direction.LEFT;
            else if (cell.row > GameConfig.toTileRow(enemy.getCollisionBounds().getCenterY())) pathDirection = Direction.DOWN;
            else if (cell.row < GameConfig.toTileRow(enemy.getCollisionBounds().getCenterY())) pathDirection = Direction.UP;
        }
        double pathSpeed = SPEED * PATH_SPEED_MULTIPLIER;
        boolean moved = false;
        // move on X if column differs
        if (Math.abs(dx) > 0.001 && (pathDirection == Direction.LEFT || pathDirection == Direction.RIGHT)) {
            double rem = dx;
            double step = Math.min(pathSpeed, Math.abs(rem));
            double move = Math.signum(rem) * step;
            boolean ok = model.moveEnemyWithCollision(enemy, move, 0);
            if (ok) {
                enemy.setDirection(pathDirection);
                moved = true;
                stuckClockMs = 0L;
            } else {
                stuckClockMs += deltaMs;
                if (stuckClockMs >= STUCK_REPLAN_MS) {
                    clearPath();
                    planPath(enemy, model, GameConfig.toTileRow(enemy.getCollisionBounds().getCenterY()), GameConfig.toTileColumn(enemy.getCollisionBounds().getCenterX()), GameConfig.toTileRow(model.getPlayer().getCollisionBounds().getCenterY()), GameConfig.toTileColumn(model.getPlayer().getCollisionBounds().getCenterX()));
                }
                return false;
            }
            // check reached
            double axisDist = Math.abs(dx);
            if (axisDist <= WAYPOINT_REACHED_DISTANCE) {
                currentPathIndex++;
                pathDirection = Direction.NONE;
            }
            return moved;
        }
        // move on Y if row differs
        if (Math.abs(dy) > 0.001 && (pathDirection == Direction.UP || pathDirection == Direction.DOWN)) {
            double rem = dy;
            double step = Math.min(pathSpeed, Math.abs(rem));
            double move = Math.signum(rem) * step;
            boolean ok = model.moveEnemyWithCollision(enemy, 0, move);
            if (ok) {
                enemy.setDirection(pathDirection);
                moved = true;
                stuckClockMs = 0L;
            } else {
                stuckClockMs += deltaMs;
                if (stuckClockMs >= STUCK_REPLAN_MS) {
                    clearPath();
                    planPath(enemy, model, GameConfig.toTileRow(enemy.getCollisionBounds().getCenterY()), GameConfig.toTileColumn(enemy.getCollisionBounds().getCenterX()), GameConfig.toTileRow(model.getPlayer().getCollisionBounds().getCenterY()), GameConfig.toTileColumn(model.getPlayer().getCollisionBounds().getCenterX()));
                }
                return false;
            }
            double axisDist = Math.abs(dy);
            if (axisDist <= WAYPOINT_REACHED_DISTANCE) {
                currentPathIndex++;
                pathDirection = Direction.NONE;
            }
            return moved;
        }
        // already at waypoint
        currentPathIndex++;
        pathDirection = Direction.NONE;
        return false;
    }

    @Override
    public String getName() {
        return "ChasePatrol";
    }
}

