package dev.mlml.systems.leveling;

import dev.mlml.systems.Serialize;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Represents a user's leveling statistics.
 * Tracks experience points, level, messages sent, and various leveling achievements.
 */
@Data
public class LevelingUser {
    private static final Logger logger = LoggerFactory.getLogger(LevelingUser.class);

    public static final String ACCOLADE_FIRST_LEVEL = "<:first_level:1234567890123456789>";
    public static final String ACCOLADE_LEVEL_10 = "<:level_10:1234567890123456789>";
    public static final String ACCOLADE_LEVEL_50 = "<:level_50:1234567890123456789>";
    public static final String ACCOLADE_LEVEL_100 = "<:level_100:1234567890123456789>";
    public static final String ACCOLADE_MESSAGE_MASTER = "<:hamster:918912275501424661>";

    @Serialize
    private long experience = 0;

    @Serialize
    private int level = 0;

    @Serialize
    private int messages = 0;

    @Serialize
    private long lastMessageTime = 0;

    @Serialize
    private int streakDays = 0;

    @Serialize
    private long lastStreakTime = 0;

    @Serialize
    private String accolades;

    @Serialize
    private final String id;

    public LevelingUser(String id) {
        this.id = id;
    }

    /**
     * Adds experience points to the user and checks for level ups.
     * @param amount the amount of experience to add
     * @return true if the user leveled up, false otherwise
     */
    public boolean addExperience(long amount) {
        if (amount <= 0) {
            logger.warn("Tried to add negative or zero experience");
            return false;
        }

        experience += amount;
        int newLevel = calculateLevel(experience);
        
        if (newLevel > level) {
            level = newLevel;
            checkLevelAccolades();
            return true;
        }
        
        return false;
    }

    /**
     * Records a message sent by the user and adds experience.
     * Implements cooldown to prevent spam.
     * @param cooldownMs the cooldown period in milliseconds
     * @param baseExp the base experience to award
     * @return the amount of experience gained, or 0 if cooldown is active
     */
    public long recordMessage(long cooldownMs, long baseExp) {
        long currentTime = System.currentTimeMillis();

        messages++;

        if (currentTime - lastMessageTime < cooldownMs) {
            return 0L;
        }

        lastMessageTime = currentTime;
        
        long expGained = baseExp + (long)(Math.random() * 10);
        addExperience(expGained);
        
        checkMessageAccolades();
        
        return expGained;
    }

    /**
     * Updates the user's daily streak.
     * @return true if streak was updated, false if already updated today
     */
    public boolean updateStreak() {
        long currentTime = System.currentTimeMillis();
        long dayInMs = 24 * 60 * 60 * 1000L;

        if (lastStreakTime == 0 || currentTime - lastStreakTime >= dayInMs * 2) {
            streakDays = 0;
            lastStreakTime = currentTime;
        }

        if (currentTime - lastStreakTime >= dayInMs) {
            streakDays++;
            lastStreakTime = currentTime;
            return true;
        }
        
        return false;
    }

    /**
     * Calculates the level based on total experience using a quadratic formula.
     * @param exp the total experience points
     * @return the calculated level
     */
    public static int calculateLevel(long exp) {
        return (int) Math.sqrt(exp / 100.0);
    }

    /**
     * Calculates the experience required for the next level.
     * @return the experience required for the next level
     */
    public long getExperienceForNextLevel() {
        int nextLevel = level + 1;
        return (long) Math.pow(nextLevel, 2) * 100;
    }

    /**
     * Calculates the progress to the next level as a percentage.
     * @return the progress percentage (0-100)
     */
    public double getProgressToNextLevel() {
        if (level == 0) {
            return 0.0;
        }
        
        long currentLevelExp = (long) Math.pow(level, 2) * 100;
        long nextLevelExp = getExperienceForNextLevel();
        long expInCurrentLevel = experience - currentLevelExp;
        long expNeededForNextLevel = nextLevelExp - currentLevelExp;
        
        return (double) expInCurrentLevel / expNeededForNextLevel * 100.0;
    }

    /**
     * Gets the accolades of the user as an array.
     * @return An array of accolades.
     */
    public String[] getAccolades() {
        if (accolades == null) {
            return new String[0];
        }
        return accolades.split(",");
    }

    /**
     * Adds an accolade to the user's accolades.
     * @param accolade The accolade to add.
     */
    public void addAccolade(String accolade) {
        if (accolades == null) {
            accolades = accolade;
            return;
        }

        if (Arrays.stream(getAccolades()).anyMatch(a -> a.equalsIgnoreCase(accolade))) {
            return;
        }

        accolades += "," + accolade;
    }

    /**
     * Checks for level-based accolades and awards them if applicable.
     */
    private void checkLevelAccolades() {
        if (level == 1) {
            addAccolade(ACCOLADE_FIRST_LEVEL);
        } else if (level == 10) {
            addAccolade(ACCOLADE_LEVEL_10);
        } else if (level == 50) {
            addAccolade(ACCOLADE_LEVEL_50);
        } else if (level == 100) {
            addAccolade(ACCOLADE_LEVEL_100);
        }
    }

    /**
     * Checks for message-based accolades and awards them if applicable.
     */
    private void checkMessageAccolades() {
        if (messages >= 10000) {
            addAccolade(ACCOLADE_MESSAGE_MASTER);
        }
    }

    /**
     * Gets the user's rank based on their level.
     * @return a string representing the user's rank
     */
    public String getRank() {
        if (level >= 100) return "Legend";
        if (level >= 50) return "Master";
        if (level >= 25) return "Veteran";
        if (level >= 10) return "Regular";
        if (level >= 5) return "Active";
        if (level >= 1) return "Newcomer";
        return "Beginner";
    }
}
