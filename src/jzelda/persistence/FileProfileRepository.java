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
 * File-based DAO for profiles. The format is deliberately simple so the project
 * can run without external JSON libraries.
 */
public class FileProfileRepository implements ProfileRepository {
    private final Path file;

    /**
     * Creates a repository writing to {@code resources/profiles/profiles.csv}.
     */
    public FileProfileRepository() {
        this(Paths.get("resources", "profiles", "profiles.csv"));
    }

    /**
     * Creates a repository writing to the supplied path.
     *
     * @param file storage path
     */
    public FileProfileRepository(Path file) {
        this.file = file;
    }

    @Override
    public List<Profile> loadProfiles() {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(line -> !line.trim().isEmpty())
                    .map(Profile::fromStorageRow).collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException | IllegalArgumentException ex) {
            System.err.println("Impossibile caricare profili: " + ex.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void saveProfiles(List<Profile> profiles) {
        try {
            Files.createDirectories(file.getParent());
            List<String> rows = profiles.stream().map(Profile::toStorageRow).collect(Collectors.toList());
            Files.write(file, rows, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("Impossibile salvare profili: " + ex.getMessage());
        }
    }
}
