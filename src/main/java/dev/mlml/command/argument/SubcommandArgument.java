package dev.mlml.command.argument;

import lombok.Getter;

import java.util.*;

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

    public List<ArgumentBase<?>> getArgumentsForSubcommand(String subcommand) {
        return subcommandArguments.getOrDefault(subcommand.toLowerCase(), Collections.emptyList());
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder extends ArgumentBase.Builder<Builder, String, SubcommandArgument> {
        private final Map<String, List<ArgumentBase<?>>> subcommandArguments = new HashMap<>();

        protected Builder(String name) {
            super(name);
        }

        public Builder addSubcommand(String subcommand, ArgumentBase<?>... arguments) {
            subcommandArguments.put(subcommand.toLowerCase(), Arrays.asList(arguments));
            return this;
        }

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