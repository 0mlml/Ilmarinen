package dev.mlml.systems.economy;

import dev.mlml.systems.Serialize;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

@Data
public class EconUser {
    private static final Logger logger = LoggerFactory.getLogger(EconUser.class);

    public static final String ACCOLADE_BETA_TESTER = "<:beta_tester:918551032273977355>";
    public static final String ACCOLADE_HIGH_ROLLER = "<a:high_roller:948584238368825364>";

    @Serialize
    private float money = 0;

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

    public void bailout(float bailout) {
        money = bailout;
        bankruptcies++;
    }

    public void play(float amount) {
        games++;
        if (amount <= 0) {
            logger.warn("Tried to play with a negative amount");
            return;
        }
        spent += amount;
        money -= amount;
    }

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

    public boolean canAfford(float amount) {
        return money > 0 && money >= amount; // I think in any case you are checking for 'canAfford' you probably want a positive amount
    }

    public float getWinRate() {
        return (float) wins / games;
    }

    public float getProfit() {
        return winnings - spent;
    }

    public float getLoss() {
        return spent - winnings;
    }

    public int getLost() {
        return games - wins;
    }

    @Serialize
    private String accolades;

    public String[] getAccolades() {
        if (accolades == null) {
            return new String[0];
        }
        return accolades.split(",");
    }

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
