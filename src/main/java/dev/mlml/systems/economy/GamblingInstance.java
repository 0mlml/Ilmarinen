package dev.mlml.systems.economy;

import lombok.Getter;

/**
 * This class exists solely as a wrapper for stats to combine the separate win calls to one
 */
public record GamblingInstance(EconUser user, EconGuild guild) {

    /**
     * Plays a game with the specified amount, updating both user and guild statistics.
     *
     * @param amount The amount to play with.
     */
    public void play(float amount) {
        user.play(amount);
        guild.play(amount);
    }

    /**
     * Records a win with the specified amount, updating both user and guild statistics.
     *
     * @param amount The amount won.
     */
    public void win(float amount) {
        user.win(amount);
        guild.win(amount);
    }
}
