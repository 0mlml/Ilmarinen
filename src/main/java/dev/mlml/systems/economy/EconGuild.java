package dev.mlml.systems.economy;

import dev.mlml.systems.Serialize;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public void play(float amount) {
        games++;
        if (amount <= 0) {
            logger.warn("Tried to play with a negative amount");
            return;
        }
        spent += amount;
    }

    public void win(float amount) {
        wins++;
        if (amount <= 0) {
            logger.warn("Tried to win with a negative amount");
            return;
        }
        winnings += amount;
    }

    public float getWinRate() {
        return (float) wins / games;
    }

    public float getProfit() {
        return winnings - spent;
    }

    @Serialize
    private final String id;

    public EconGuild(String id) {
        this.id = id;
    }
}
