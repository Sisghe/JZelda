package jzelda.audio;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Singleton audio service compatible with modern JDKs. It uses
 * {@link javax.sound.sampled.AudioInputStream} and {@link Clip} rather than the
 * obsolete {@code sun.audio} package.
 */
public final class AudioManager {
    private static AudioManager instance;
    private final Map<String, Long> lastPlayed = new ConcurrentHashMap<>();
    private float volume = 0.80f;

    private AudioManager() {
        // Singleton.
    }

    /**
     * Returns the single audio manager instance.
     *
     * @return singleton instance
     */
    public static synchronized AudioManager getInstance() {
        if (instance == null) {
            instance = new AudioManager();
        }
        return instance;
    }

    /**
     * Plays a WAV sample from the file system. Errors are printed but do not stop
     * the game loop, allowing placeholder projects to run even when audio hardware
     * is unavailable.
     *
     * @param filename path to a WAV file, for example
     *                 {@code resources/audio/hit.wav}
     */
    public void play(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastPlayed.getOrDefault(filename, 0L);
        if (now - last < 50L) {
            return;
        }
        lastPlayed.put(filename, now);
        try (InputStream in = new BufferedInputStream(new FileInputStream(filename));
                AudioInputStream audioIn = AudioSystem.getAudioInputStream(in)) {
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            applyVolume(clip);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE) {
                    clip.close();
                }
            });
            clip.start();
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException ex) {
            System.err.println("Audio non disponibile per '" + filename + "': " + ex.getMessage());
        }
    }

    /**
     * Plays a named effect stored under {@code resources/audio/}.
     *
     * @param effectName base file name without path, for example {@code hit.wav}
     */
    public void playEffect(String effectName) {
        play("resources/audio/" + effectName);
    }

    /**
     * Sets global sample volume for future clips.
     *
     * @param volume value between {@code 0.0f} and {@code 1.0f}
     */
    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
    }

    /**
     * @return current global volume in the range {@code 0.0f..1.0f}
     */
    public float getVolume() {
        return volume;
    }

    private void applyVolume(Clip clip) {
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float min = gain.getMinimum();
            float max = gain.getMaximum();
            float db = min + (max - min) * volume;
            gain.setValue(db);
        }
    }
}
