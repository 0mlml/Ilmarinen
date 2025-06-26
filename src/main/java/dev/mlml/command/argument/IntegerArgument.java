package dev.mlml.command.argument;

/**
 * Represents an Integer argument for command parsing.
 */
public class IntegerArgument extends ArgumentBase<Integer> {
    final int min;
    final int max;

    public IntegerArgument(String name, String description, boolean isRequired, int min, int max) {
        super(name, description, isRequired);

        this.min = min;
        this.max = max;
    }

    /**
     * Parses the input string to an Integer value.
     * It checks if the value is within the specified range.
     *
     * @param input The input string to parse.
     * @return The parsed Integer value.
     */
    @Override
    public Integer parse(String input) {
        int value = Integer.parseInt(input);
        if (value < min || value > max) {
            throw new IllegalArgumentException("Value must be between " + min + " and " + max);
        }
        return value;
    }

    /**
     * Builder for creating instances of IntegerArgument.
     * Allows setting the name, description, whether the argument is required,
     * and the minimum and maximum values for the integer.
     */
    public static class Builder extends ArgumentBase.Builder<IntegerArgument.Builder, Integer, IntegerArgument> {
        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        /**
         * Constructs a new Builder with the specified name.
         *
         * @param name The name of the Integer argument.
         */
        public Builder(String name) {
            super(name);
        }

        /**
         * Sets the minimum value for the Integer argument.
         *
         * @param min The minimum value.
         * @return This Builder instance for method chaining.
         */
        public Builder min(int min) {
            this.min = min;
            return getThis();
        }

        /**
         * Sets the maximum value for the Integer argument.
         *
         * @param max The maximum value.
         * @return This Builder instance for method chaining.
         */
        public Builder max(int max) {
            this.max = max;
            return getThis();
        }

        /**
         * Creates a new IntegerArgument instance with the specified properties.
         *
         * @return A new IntegerArgument instance.
         */
        @Override
        public IntegerArgument get() {
            return new IntegerArgument(name, description, isRequired, min, max);
        }
    }
}
