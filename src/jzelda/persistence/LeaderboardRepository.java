package jzelda.persistence;

import java.util.List;

/**
 * DAO abstraction for leaderboard persistence.
 */
public interface LeaderboardRepository {
    /**
     * Loads all score entries.
     *
     * @return score entries
     */
    List<ScoreEntry> loadEntries();

    /**
     * Saves score entries.
     *
     * @param entries entries to persist
     */
    void saveEntries(List<ScoreEntry> entries);
}
