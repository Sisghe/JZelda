package jzelda.persistence;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One leaderboard row containing score, reached level and result.
 */
public class ScoreEntry {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final String nickname;
    private final int score;
    private final int levelReached;
    private final boolean victory;
    private final LocalDateTime dateTime;

    /**
     * Creates a score entry.
     *
     * @param nickname profile nickname
     * @param score final score
     * @param levelReached highest level reached
     * @param victory whether the run ended in victory
     * @param dateTime timestamp
     */
    public ScoreEntry(String nickname, int score, int levelReached, boolean victory, LocalDateTime dateTime) {
        this.nickname = nickname;
        this.score = score;
        this.levelReached = levelReached;
        this.victory = victory;
        this.dateTime = dateTime;
    }

    /**
     * @return profile nickname
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * @return score value
     */
    public int getScore() {
        return score;
    }

    /**
     * @return highest reached level
     */
    public int getLevelReached() {
        return levelReached;
    }

    /**
     * @return {@code true} for winning runs
     */
    public boolean isVictory() {
        return victory;
    }

    /**
     * @return score timestamp
     */
    public LocalDateTime getDateTime() {
        return dateTime;
    }

    /**
     * @return storage row
     */
    public String toStorageRow() {
        return escape(nickname) + "|" + score + "|" + levelReached + "|" + victory + "|" + dateTime.format(FORMATTER);
    }

    /**
     * Parses a storage row.
     *
     * @param row row to parse
     * @return score entry
     */
    public static ScoreEntry fromStorageRow(String row) {
        String[] parts = row.split("\\|", -1);
        if (parts.length < 5) {
            throw new IllegalArgumentException("Riga classifica non valida: " + row);
        }
        return new ScoreEntry(unescape(parts[0]), parseInt(parts[1]), parseInt(parts[2]), Boolean.parseBoolean(parts[3]),
                LocalDateTime.parse(parts[4], FORMATTER));
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("|", "%7C").replace("\n", " ");
    }

    private static String unescape(String value) {
        return value.replace("%7C", "|");
    }
}
