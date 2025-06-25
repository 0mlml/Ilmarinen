package dev.mlml.command.argument;

/**
 * Represents a Boolean argument for command parsing.
 */
public class BooleanArgument extends ArgumentBase<Boolean> {
    public BooleanArgument(String name, String description, boolean isRequired) {
        super(name, description, isRequired);
    }

    /**
     * Parses the input string to a Boolean value.
     * Returns true if the input is "true" (case-insensitive), false otherwise.
     *
     * @param input The input string to parse.
     * @return The parsed Boolean value.
     */
    @Override
    public Boolean parse(String input) {
        return Boolean.parseBoolean(input);
    }

    /**
     * Builder for creating instances of BooleanArgument.
     * Allows setting the name, description, and whether the argument is required.
     */
    public static class Builder extends ArgumentBase.Builder<BooleanArgument.Builder, Boolean, BooleanArgument> {
        /**
         * Constructs a new Builder with the specified name.
         *
         * @param name The name of the Boolean argument.
         */
        public Builder(String name) {
            super(name);
        }

        /**
         * Creates a new BooleanArgument instance with the specified properties.
         * @return A new BooleanArgument instance.
         */
        @Override
        public BooleanArgument get() {
            return new BooleanArgument(name, description, isRequired);
        }
    }
}
