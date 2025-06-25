package dev.mlml.command.argument;

import dev.mlml.Ilmarinen;
import dev.mlml.Utils;
import net.dv8tion.jda.api.entities.User;

/**
 * UserArgument is an argument type that represents a Discord user.
 * It can parse user mentions or user IDs from a string input.
 */
public class UserArgument extends ArgumentBase<User> {
    public UserArgument(String name, String description, boolean isRequired) {
        super(name, description, isRequired);
    }

    /**
     * Parses the input string to retrieve a User object.
     * It checks if the input is a user mention or a snowflake (user ID).
     *
     * @param input The input string to parse.
     * @return The User object if found, or null if not found or invalid input.
     */
    @Override
    public User parse(String input) {
        if (Utils.stringIsUserMention(input)) {
            String id = input.contains("!")
                        ? input.substring(3, input.length() - 1)
                        : input.substring(2, input.length() - 1);
            return Ilmarinen.getJda().retrieveUserById(id).complete();
        }
        if (Utils.stringIsSnowflake(input)) {
            return Ilmarinen.getJda().retrieveUserById(input).complete();
        }
        return null;
    }

    /**
     * Builder for creating instances of UserArgument.
     */
    public static class Builder extends ArgumentBase.Builder<UserArgument.Builder, User, UserArgument> {
        /**
         * Constructs a new Builder with the specified name.
         *
         * @param name The name of the user argument.
         */
        public Builder(String name) {
            super(name);
        }

        /**
         * Creates a new UserArgument instance with the specified properties.
         *
         * @return A new UserArgument instance.
         */
        @Override
        public UserArgument get() {
            return new UserArgument(name, description, isRequired);
        }
    }
}