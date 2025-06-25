package dev.mlml.command.argument;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents an argument that can take a specific set of options.
 * The input must match one of the predefined options to be valid.
 */
@Getter
public class OptionArgument extends ArgumentBase<String> {
    final String[] options;

    public OptionArgument(String name, String description, boolean isRequired, String[] options) {
        super(name, description, isRequired);

        this.options = options;
    }

    /**
     * Parses the input string to check if it matches one of the predefined options.
     * If the input does not match any option, it returns null.
     *
     * @param input The input string to parse.
     * @return The input string if it matches an option, or null if it does not.
     */
    @Override
    public String parse(String input) {
        if (Arrays.stream(options).noneMatch(option -> option.equalsIgnoreCase(input))) {
            return null;
        }
        return input;
    }

    /**
     * Returns a description of the argument, including its options.
     *
     * @return A string describing the argument and its options.
     */
    @Override
    public String getDescription() {
        return super.getDescription() + " - Options: " + String.join(", ", options);
    }

    /**
     * Builder for creating instances of OptionArgument.
     * Allows adding multiple options and setting the name, description, and whether the argument is required.
     */
    public static class Builder extends ArgumentBase.Builder<OptionArgument.Builder, String, OptionArgument> {
        List<String> options = new ArrayList<>();

        /**
         * Constructs a new Builder with the specified name.
         *
         * @param name The name of the option argument.
         */
        public Builder(String name) {
            super(name);
        }

        /**
         * Adds a single option to the argument.
         *
         * @param option The option to add.
         * @return This Builder instance for method chaining.
         */
        public Builder addOption(String option) {
            options.add(option);
            return getThis();
        }

        /**
         * Adds multiple options to the argument.
         *
         * @param options The list of options to add.
         * @return This Builder instance for method chaining.
         */
        public Builder addOptions(List<String> options) {
            this.options.addAll(options);
            return getThis();
        }

        /**
         * Creates a new OptionArgument instance with the specified properties.
         * @return A new OptionArgument instance.
         */
        @Override
        public OptionArgument get() {
            return new OptionArgument(name, description, isRequired, options.toArray(new String[0]));
        }
    }
}
