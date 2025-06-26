package dev.mlml.systems.leveling;

import dev.mlml.systems.Serialize;
import lombok.Data;

/**
 * Represents global leveling statistics and settings.
 * Tracks overall leveling data across all guilds.
 */
@Data
public class LevelingGlobal {
    @Serialize
    private long totalExperienceAwarded = 0;

    @Serialize
    private long totalMessagesProcessed = 0;

    @Serialize
    private long totalLevelUps = 0;

    @Serialize
    private boolean globalLevelingEnabled = true;

    @Serialize
    private long globalMessageCooldownMs = 60000; // 1 minute default

    @Serialize
    private long globalBaseExperiencePerMessage = 15;

    /**
     * Increments the total experience awarded across all guilds.
     * @param amount the amount of experience to add to the total
     */
    public void addTotalExperience(long amount) {
        this.totalExperienceAwarded += amount;
    }

    /**
     * Increments the total messages processed across all guilds.
     */
    public void incrementTotalMessages() {
        this.totalMessagesProcessed++;
    }

    /**
     * Increments the total level-ups across all guilds.
     */
    public void incrementTotalLevelUps() {
        this.totalLevelUps++;
    }

    /**
     * Calculates the average experience per message.
     * @return the average experience per message, or 0 if no messages have been processed
     */
    public double getAverageExperiencePerMessage() {
        if (totalMessagesProcessed == 0) {
            return 0.0;
        }
        return (double) totalExperienceAwarded / totalMessagesProcessed;
    }

    /**
     * Calculates the average messages per level-up.
     * @return the average messages per level-up, or 0 if no level-ups have occurred
     */
    public double getAverageMessagesPerLevelUp() {
        if (totalLevelUps == 0) {
            return 0.0;
        }
        return (double) totalMessagesProcessed / totalLevelUps;
    }
} 