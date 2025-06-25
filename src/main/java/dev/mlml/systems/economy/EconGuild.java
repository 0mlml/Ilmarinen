package dev.mlml.systems.economy;

import dev.mlml.systems.Serialize;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a guild's economy statistics.
 * Tracks games played, wins, winnings, and spent amounts.
 */
@Data
public class EconGuild {
    private static final Logger logger = LoggerFactory.getLogger(EconGuild.class);

    @Serialize
    private int games;
    @Serialize
    private int wins;
    @Serialize
    private float winnings;
    @Serialize
    private float spent;

    // TODO: Implement a better way to update these on play/win
    /**
     * Records a game played with a specified amount.
     * Increments the games count and adds to the spent amount.
     *
     * @param amount The amount spent in the game.
     */
    public void play(float amount) {
        games++;
        if (amount <= 0) {
            logger.warn("Tried to play with a negative amount");
            return;
        }
        spent += amount;
    }

    /**
     * Records a win with a specified amount.
     * Increments the wins count and adds to the winnings.
     *
     * @param amount The amount won in the game.
     */
    public void win(float amount) {
        wins++;
        if (amount <= 0) {
            logger.warn("Tried to win with a negative amount");
            return;
        }
        winnings += amount;
    }

    /**
     * Calculates the win rate as a percentage of games won.
     *
     * @return The win rate as a float.
     */
    public float getWinRate() {
        return (float) wins / games;
    }

    /**
     * Calculates the profit made by the guild.
     * Profit is defined as winnings minus spent amount.
     *
     * @return The profit as a float.
     */
    public float getProfit() {
        return winnings - spent;
    }

    @Serialize
    private final String id;

    public EconGuild(String id) {
        this.id = id;
    }
}
