package dev.mlml.command.argument;


import dev.mlml.Ilmarinen;
import dev.mlml.Utils;
import net.dv8tion.jda.api.entities.channel.Channel;

/**
 * Represents a command argument for a Discord channel.
 */
public class ChannelArgument extends ArgumentBase<Channel> {
    public ChannelArgument(String name, String description, boolean isRequired) {
        super(name, description, isRequired);
    }

    /**
     * Parses the input string to a Channel object.
     * It checks if the input is a channel mention or a snowflake ID.
     *
     * @param input The input string to parse.
     * @return The parsed Channel object, or null if parsing fails.
     */
    @Override
    public Channel parse(String input) {
        if (Utils.stringIsChannelMention(input)) {
            return Ilmarinen.getJda().getTextChannelById(input.substring(2, input.length() - 1));
        }
        if (Utils.stringIsSnowflake(input)) {
            return Ilmarinen.getJda().getTextChannelById(input);
        }
        return null;
    }

    /**
     * Builder for creating instances of ChannelArgument.
     * Allows setting the name, description, and whether the argument is required.
     */
    public static class Builder extends ArgumentBase.Builder<ChannelArgument.Builder, Channel, ChannelArgument> {
        /**
         * Constructs a new Builder with the specified name.
         *
         * @param name The name of the channel argument.
         */
        public Builder(String name) {
            super(name);
        }

        /**
         * Creates a new ChannelArgument instance with the specified properties.
         *
         * @return A new ChannelArgument instance.
         */
        @Override
        public ChannelArgument get() {
            return new ChannelArgument(name, description, isRequired);
        }
    }
}