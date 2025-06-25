package dev.mlml.systems.economy;

import dev.mlml.systems.Serialize;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Represents a user's economy statistics.
 * Tracks money, games played, wins, winnings, spent amounts, bankruptcies, and accolades.
 */
@Data
public class EconUser {
    private static final Logger logger = LoggerFactory.getLogger(EconUser.class);

    public static final String ACCOLADE_BETA_TESTER = "<:beta_tester:918551032273977355>";
    public static final String ACCOLADE_HIGH_ROLLER = "<a:high_roller:948584238368825364>";

    @Serialize
    private float money = 0;

    /**
     * Adds a specified amount of money to the user's balance.
     * @param amount the amount of money to add
     */
    public void addMoney(float amount) {
        money += amount;
    }

    @Serialize
    private int games = 0;
    @Serialize
    private int wins = 0;
    @Serialize
    private float winnings = 0;
    @Serialize
    private float spent = 0;

    @Serialize
    private int bankruptcies = 0;

    /**
     * Records a bankruptcy event, setting the user's money to the specified bailout amount.
     * Increments the bankruptcy count.
     *
     * @param bailout The amount of money to set after bankruptcy.
     */
    public void bailout(float bailout) {
        money = bailout;
        bankruptcies++;
    }

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
        money -= amount;
    }

    /**
     * Records a win with a specified amount.
     * Increments the wins count, adds to the winnings, and updates the user's money.
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
        money += amount;

        if (winnings > 10000) {
            addAccolade(ACCOLADE_HIGH_ROLLER);
        }
    }

    /**
     * Checks if the user can afford a specified amount.
     * Returns true if the user has money and the amount is positive and less than or equal to the user's balance.
     *
     * @param amount The amount to check affordability for.
     * @return true if the user can afford the amount, false otherwise.
     */
    public boolean canAfford(float amount) {
        return money > 0 && money >= amount; // I think in any case you are checking for 'canAfford' you probably want a positive amount
    }

    /**
     * Calculates the win rate as a percentage of games won.
     * Returns 0 if no games have been played to avoid division by zero.
     *
     * @return The win rate as a float.
     */
    public float getWinRate() {
        if (games == 0) {
            return 0f;
        }
        return (float) wins / games;
    }

    /**
     * Calculates the profit made by the user.
     * Profit is defined as winnings minus spent amount.
     *
     * @return The profit as a float.
     */
    public float getProfit() {
        return winnings - spent;
    }

    /**
     * Calculates the total loss incurred by the user.
     * Loss is defined as spent amount minus winnings.
     *
     * @return The loss as a float.
     */
    public float getLoss() {
        return spent - winnings;
    }

    /**
     * Gets the number of games lost by the user.
     * This is calculated as total games played minus wins.
     *
     * @return The number of lost games.
     */
    public int getLost() {
        return games - wins;
    }

    @Serialize
    private String accolades;

    /**
     * Gets the accolades of the user as an array.
     * If no accolades are set, returns an empty array.
     *
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
     * If the accolade is already present, it will not be added again.
     * If accolades are null, it initializes them with the new accolade.
     *
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

    @Serialize
    private final String id;

    public EconUser(String id) {
        this.id = id;
    }
}
