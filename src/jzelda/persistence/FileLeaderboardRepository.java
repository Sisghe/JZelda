package jzelda.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * File-based leaderboard DAO writing to {@code resources/profiles/leaderboard.csv}.
 */
public class FileLeaderboardRepository implements LeaderboardRepository {
    private final Path file;

    /** Creates a repository with the default storage path. */
    public FileLeaderboardRepository() {
        this(Paths.get("resources", "profiles", "leaderboard.csv"));
    }

    /**
     * Creates a repository with a custom path.
     *
     * @param file file path
     */
    public FileLeaderboardRepository(Path file) {
        this.file = file;
    }

    @Override
    public List<ScoreEntry> loadEntries() {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(line -> !line.trim().isEmpty())
                    .map(ScoreEntry::fromStorageRow).collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException | IllegalArgumentException ex) {
            System.err.println("Impossibile caricare classifica: " + ex.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void saveEntries(List<ScoreEntry> entries) {
        try {
            Files.createDirectories(file.getParent());
            List<String> rows = entries.stream().map(ScoreEntry::toStorageRow).collect(Collectors.toList());
            Files.write(file, rows, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("Impossibile salvare classifica: " + ex.getMessage());
        }
    }
}
