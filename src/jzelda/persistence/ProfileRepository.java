package jzelda.persistence;

import java.util.List;

/**
 * DAO abstraction for profile persistence.
 */
public interface ProfileRepository {
    /**
     * Loads profiles from storage.
     *
     * @return loaded profiles
     */
    List<Profile> loadProfiles();

    /**
     * Saves all profiles to storage.
     *
     * @param profiles profiles to save
     */
    void saveProfiles(List<Profile> profiles);
}
