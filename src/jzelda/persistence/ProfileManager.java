package jzelda.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Application service that manages profile creation, selection, statistics and
 * persistence.
 */
public class ProfileManager {
    private final ProfileRepository repository;
    private final List<Profile> profiles = new ArrayList<>();
    private Profile currentProfile;

    /**
     * Creates a manager and loads existing profiles.
     *
     * @param repository profile DAO
     */
    public ProfileManager(ProfileRepository repository) {
        this.repository = repository;
        load();
    }

    /**
     * Loads profiles and creates a demo profile when storage is empty.
     */
    public final void load() {
        profiles.clear();
        profiles.addAll(repository.loadProfiles());
        if (profiles.isEmpty()) {
            profiles.add(new Profile("Avventuriero", "foglia"));
        }
        currentProfile = profiles.get(0);
    }

    /**
     * Persists every profile.
     */
    public void save() {
        repository.saveProfiles(profiles);
    }

    /**
     * Creates a new profile if the nickname is unique.
     *
     * @param nickname requested nickname
     * @param avatar avatar identifier
     * @return created or existing profile
     */
    public Profile createProfile(String nickname, String avatar) {
        String clean = sanitizeNickname(nickname);
        Optional<Profile> existing = findByNickname(clean);
        if (existing.isPresent()) {
            currentProfile = existing.get();
            return currentProfile;
        }
        Profile profile = new Profile(clean, avatar == null || avatar.isBlank() ? "foglia" : avatar);
        profiles.add(profile);
        currentProfile = profile;
        save();
        return profile;
    }

    /**
     * Selects a profile by list index.
     *
     * @param index profile index
     * @return selected profile
     */
    public Profile selectByIndex(int index) {
        if (profiles.isEmpty()) {
            currentProfile = createProfile("Avventuriero", "foglia");
        } else {
            int bounded = Math.max(0, Math.min(index, profiles.size() - 1));
            currentProfile = profiles.get(bounded);
        }
        return currentProfile;
    }

    /**
     * Selects a profile by nickname.
     *
     * @param nickname nickname to search
     * @return selected profile if present
     */
    public Optional<Profile> selectByNickname(String nickname) {
        Optional<Profile> result = findByNickname(nickname);
        result.ifPresent(profile -> currentProfile = profile);
        return result;
    }

    /**
     * Finds a profile by nickname using a stream filter.
     *
     * @param nickname nickname to search
     * @return optional profile
     */
    public Optional<Profile> findByNickname(String nickname) {
        return profiles.stream().filter(profile -> profile.getNickname().equalsIgnoreCase(nickname)).findFirst();
    }

    /**
     * @return immutable profiles sorted by nickname for display
     */
    public List<Profile> getProfilesSorted() {
        return Collections.unmodifiableList(profiles.stream().sorted(Comparator.comparing(Profile::getNickname))
                .collect(Collectors.toList()));
    }

    /**
     * @return mutable-order profile list for menu selection
     */
    public List<Profile> getProfiles() {
        return Collections.unmodifiableList(profiles);
    }

    /**
     * @return selected profile, never null after construction
     */
    public Profile getCurrentProfile() {
        return currentProfile;
    }

    /**
     * Calculates total games using a stream map/reduce operation.
     *
     * @return total games played by all profiles
     */
    public int calculateTotalGames() {
        return profiles.stream().map(Profile::getGamesPlayed).reduce(0, Integer::sum);
    }

    private String sanitizeNickname(String nickname) {
        String clean = nickname == null ? "" : nickname.trim();
        if (clean.isEmpty()) {
            clean = "Giocatore" + (profiles.size() + 1);
        }
        return clean.length() > 16 ? clean.substring(0, 16) : clean;
    }
}
