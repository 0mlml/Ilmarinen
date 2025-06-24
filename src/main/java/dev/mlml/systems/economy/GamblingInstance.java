package dev.mlml.systems.economy;

import lombok.Getter;

/**
 * This class exists solely as a wrapper for stats to combine the separate win calls to one
 */
@Getter
public class GamblingInstance {
    private final EconUser user;
    private final EconGuild guild;

    public GamblingInstance(EconUser user, EconGuild guild) {
        this.user = user;
        this.guild = guild;
    }

    public void play(float amount) {
        user.play(amount);
        guild.play(amount);
    }

    public void win(float amount) {
        user.win(amount);
        guild.win(amount);
    }
}
