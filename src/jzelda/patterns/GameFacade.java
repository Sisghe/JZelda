package jzelda.patterns;

import jzelda.audio.AudioManager;
import jzelda.persistence.Leaderboard;
import jzelda.persistence.ProfileManager;
import jzelda.resources.ResourceManager;

/**
 * Facade that exposes the main infrastructure subsystems through a single
 * object. Controllers and the model use it to access resources, audio and
 * persistence without coupling themselves to file-system details.
 */
public final class GameFacade {
    private final ResourceManager resources;
    private final AudioManager audio;
    private final ProfileManager profileManager;
    private final Leaderboard leaderboard;

    /**
     * Builds the facade from already configured subsystems.
     *
     * @param resources resource manager singleton
     * @param audio audio manager singleton
     * @param profileManager profile persistence service
     * @param leaderboard score persistence service
     */
    public GameFacade(ResourceManager resources, AudioManager audio, ProfileManager profileManager,
            Leaderboard leaderboard) {
        this.resources = resources;
        this.audio = audio;
        this.profileManager = profileManager;
        this.leaderboard = leaderboard;
    }

    /**
     * @return resource loading subsystem
     */
    public ResourceManager resources() {
        return resources;
    }

    /**
     * @return audio subsystem
     */
    public AudioManager audio() {
        return audio;
    }

    /**
     * @return profile persistence subsystem
     */
    public ProfileManager profiles() {
        return profileManager;
    }

    /**
     * @return leaderboard persistence subsystem
     */
    public Leaderboard leaderboard() {
        return leaderboard;
    }
}
