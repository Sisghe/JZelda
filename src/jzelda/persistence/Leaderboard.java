package jzelda.persistence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Leaderboard service. It uses Java Stream pipelines to filter, sort and select
 * top results.
 */
public class Leaderboard {
    private final LeaderboardRepository repository;
    private final List<ScoreEntry> entries = new ArrayList<>();

    /**
     * Creates a leaderboard and loads existing entries.
     *
     * @param repository leaderboard DAO
     */
    public Leaderboard(LeaderboardRepository repository) {
        this.repository = repository;
        load();
    }

    /** Loads entries from persistence. */
    public final void load() {
        entries.clear();
        entries.addAll(repository.loadEntries());
    }

    /** Saves entries to persistence. */
    public void save() {
        repository.saveEntries(entries);
    }

    /**
     * Adds a finished run entry and persists it.
     *
     * @param nickname profile nickname
     * @param score final score
     * @param levelReached reached level
     * @param victory whether the run was won
     */
    public void addEntry(String nickname, int score, int levelReached, boolean victory) {
        entries.add(new ScoreEntry(nickname, score, levelReached, victory, LocalDateTime.now()));
        save();
    }

    /**
     * Returns top scores sorted descending, using filter/sorted/limit/collect.
     *
     * @param limit maximum number of entries
     * @return top score entries
     */
    public List<ScoreEntry> topScores(int limit) {
        return entries.stream().filter(entry -> entry.getScore() >= 0)
                .sorted(Comparator.comparingInt(ScoreEntry::getScore).reversed().thenComparing(ScoreEntry::getDateTime))
                .limit(limit).collect(Collectors.toList());
    }

    /**
     * @return best score value or zero when the leaderboard is empty
     */
    public int bestScore() {
        return entries.stream().map(ScoreEntry::getScore).max(Integer::compareTo).orElse(0);
    }

    /**
     * @return immutable copy of all entries
     */
    public List<ScoreEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }
}
