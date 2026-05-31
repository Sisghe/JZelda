package jzelda.persistence;

/**
 * Persistent player profile containing nickname, avatar and aggregate game
 * statistics.
 */
public class Profile {
    private final String nickname;
    private String avatar;
    private int gamesPlayed;
    private int gamesWon;
    private int gamesLost;
    private int bestScore;

    /**
     * Creates a new profile.
     *
     * @param nickname unique nickname
     * @param avatar avatar identifier
     */
    public Profile(String nickname, String avatar) {
        this.nickname = nickname;
        this.avatar = avatar;
    }

    /**
     * @return unique nickname
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * @return avatar identifier
     */
    public String getAvatar() {
        return avatar;
    }

    /**
     * Changes avatar identifier.
     *
     * @param avatar avatar identifier
     */
    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    /**
     * @return games finished by this profile
     */
    public int getGamesPlayed() {
        return gamesPlayed;
    }

    /**
     * @return games won by this profile
     */
    public int getGamesWon() {
        return gamesWon;
    }

    /**
     * @return games lost by this profile
     */
    public int getGamesLost() {
        return gamesLost;
    }

    /**
     * @return best score ever reached
     */
    public int getBestScore() {
        return bestScore;
    }

    /**
     * Records a finished game and updates the best score.
     *
     * @param won whether the game was won
     * @param score final score
     */
    public void recordResult(boolean won, int score) {
        gamesPlayed++;
        if (won) {
            gamesWon++;
        } else {
            gamesLost++;
        }
        bestScore = Math.max(bestScore, score);
    }

    /**
     * Calculates win rate as a percentage.
     *
     * @return percentage between {@code 0.0} and {@code 100.0}
     */
    public double getWinRate() {
        return gamesPlayed == 0 ? 0.0 : (gamesWon * 100.0) / gamesPlayed;
    }

    /**
     * Serializes the profile as a simple pipe-separated row.
     *
     * @return CSV-like row
     */
    public String toStorageRow() {
        return escape(nickname) + "|" + escape(avatar) + "|" + gamesPlayed + "|" + gamesWon + "|" + gamesLost + "|"
                + bestScore;
    }

    /**
     * Parses a profile row.
     *
     * @param row stored row
     * @return parsed profile
     */
    public static Profile fromStorageRow(String row) {
        String[] parts = row.split("\\|", -1);
        if (parts.length < 6) {
            throw new IllegalArgumentException("Riga profilo non valida: " + row);
        }
        Profile profile = new Profile(unescape(parts[0]), unescape(parts[1]));
        profile.gamesPlayed = parseInt(parts[2]);
        profile.gamesWon = parseInt(parts[3]);
        profile.gamesLost = parseInt(parts[4]);
        profile.bestScore = parseInt(parts[5]);
        return profile;
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
