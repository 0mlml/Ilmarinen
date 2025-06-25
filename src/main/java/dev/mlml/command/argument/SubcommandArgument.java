package dev.mlml.command.argument;

import lombok.Getter;

import java.util.*;

/**
 * Represents an argument that can take a specific set of subcommands.
 * Each subcommand can have its own set of arguments.
 */
@Getter
public class SubcommandArgument extends ArgumentBase<String> {
    private final Set<String> validSubcommands;
    private final Map<String, List<ArgumentBase<?>>> subcommandArguments;

    private SubcommandArgument(String name, String description, boolean required,
                               Set<String> validSubcommands,
                               Map<String, List<ArgumentBase<?>>> subcommandArguments) {
        super(name, description, required);
        this.validSubcommands = validSubcommands;
        this.subcommandArguments = subcommandArguments;
    }

    /**
     * Parses the input string to determine if it matches one of the valid subcommands.
     * If the input is null or empty, it returns null.
     * If the input does not match any valid subcommand, it throws an IllegalArgumentException.
     *
     * @param input The input string to parse.
     * @return The matched subcommand as a lowercase string, or null if the input is empty.
     */
    @Override
    public String parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String subcommand = input.trim().toLowerCase();
        if (!validSubcommands.contains(subcommand)) {
            throw new IllegalArgumentException("Invalid subcommand: " + subcommand +
                                                       ". Valid options: " + String.join(", ", validSubcommands));
        }

        return subcommand;
    }

    /**
     * Returns a help description for this subcommand argument, including its name,
     * whether it is required, and its description.
     *
     * @return A formatted string describing the subcommand argument.
     */
    public List<ArgumentBase<?>> getArgumentsForSubcommand(String subcommand) {
        return subcommandArguments.getOrDefault(subcommand.toLowerCase(), Collections.emptyList());
    }

    /**
     * Creates a new Builder for constructing a SubcommandArgument.
     * @param name The name of the subcommand argument.
     * @return A new Builder instance for the SubcommandArgument.
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Builder for creating instances of SubcommandArgument.
     * Allows adding multiple subcommands and their associated arguments.
     */
    public static class Builder extends ArgumentBase.Builder<Builder, String, SubcommandArgument> {
        private final Map<String, List<ArgumentBase<?>>> subcommandArguments = new HashMap<>();

        protected Builder(String name) {
            super(name);
        }

        /**
         * Adds a subcommand with its associated arguments.
         * The subcommand name is converted to lowercase for consistency.
         *
         * @param subcommand The name of the subcommand.
         * @param arguments  The arguments associated with the subcommand.
         * @return This Builder instance for method chaining.
         */
        public Builder addSubcommand(String subcommand, ArgumentBase<?>... arguments) {
            subcommandArguments.put(subcommand.toLowerCase(), Arrays.asList(arguments));
            return this;
        }

        /**
         * Creates a new SubcommandArgument instance with the specified properties.
         * @return A new SubcommandArgument instance.
         */
        @Override
        public SubcommandArgument get() {
            return new SubcommandArgument(name, description, isRequired,
                                          subcommandArguments.keySet(), subcommandArguments);
        }

        @Override
        protected Builder getThis() {
            return this;
        }
    }
}