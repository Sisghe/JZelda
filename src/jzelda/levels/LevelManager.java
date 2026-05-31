package jzelda.levels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Coordinates level progression for the campaign.
 */
public class LevelManager {
    private final LevelFactory levelFactory;
    private List<Level> levels = new ArrayList<>();
    private int currentIndex;

    /**
     * Creates a manager using the supplied factory.
     *
     * @param levelFactory factory used to load maps
     */
    public LevelManager(LevelFactory levelFactory) {
        this.levelFactory = levelFactory;
        reloadCampaign();
    }

    /**
     * Reloads all eight levels from resources using a stream pipeline.
     */
    public final void reloadCampaign() {
        levels = IntStream.rangeClosed(1, 8).mapToObj(levelFactory::createLevel).collect(Collectors.toList());
        currentIndex = 0;
    }

    /**
     * Reloads only the current level after a continue or retry.
     */
    public void reloadCurrentLevel() {
        int currentId = getCurrentLevel().getId();
        levels.set(currentIndex, levelFactory.createLevel(currentId));
    }

    /**
     * @return current level
     */
    public Level getCurrentLevel() {
        return levels.get(currentIndex);
    }

    /**
     * @return immutable list of loaded levels
     */
    public List<Level> getLevels() {
        return Collections.unmodifiableList(levels);
    }

    /**
     * @return current index, zero based
     */
    public int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * @return current level number, one based
     */
    public int getCurrentNumber() {
        return currentIndex + 1;
    }

    /**
     * Moves to the next level when available.
     *
     * @return {@code true} if there is a next level
     */
    public boolean nextLevel() {
        if (currentIndex + 1 < levels.size()) {
            currentIndex++;
            return true;
        }
        return false;
    }

    /**
     * @return {@code true} when the current level is the last one
     */
    public boolean isLastLevel() {
        return currentIndex == levels.size() - 1;
    }

    /**
     * @return {@code true} when every level is marked completed
     */
    public boolean allLevelsCompleted() {
        return levels.stream().allMatch(Level::isCompleted);
    }
}
