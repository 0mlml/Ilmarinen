package dev.mlml.command.argument;

/**
 * Represents a Float argument for command parsing.
 */
public class FloatArgument extends ArgumentBase<Float> {
    final float min;
    final float max;
    final int precision;

    public FloatArgument(String name, String description, boolean isRequired, float min, float max, int precision) {
        super(name, description, isRequired);

        this.min = min;
        this.max = max;
        this.precision = precision;
    }

    /**
     * Parses the input string to a Float value.
     * It checks if the value is within the specified range and formats it to the specified precision.
     *
     * @param input The input string to parse.
     * @return The parsed Float value, formatted to the specified precision.
     */
    @Override
    public Float parse(String input) {
        return Float.parseFloat(input);
    }

    /**
     * Builder for creating instances of FloatArgument.
     */
    public static class Builder extends ArgumentBase.Builder<FloatArgument.Builder, Float, FloatArgument> {
        float min = Float.MIN_VALUE;
        float max = Float.MAX_VALUE;
        int precision = 2;

        /**
         * Constructs a new Builder with the specified name.
         *
         * @param name The name of the Float argument.
         */
        public Builder(String name) {
            super(name);
        }

        /**
         * Sets the minimum value for the Float argument.
         *
         * @param min The minimum value.
         * @return This Builder instance for method chaining.
         */
        public Builder min(float min) {
            this.min = min;
            return getThis();
        }

        /**
         * Sets the maximum value for the Float argument.
         *
         * @param max The maximum value.
         * @return This Builder instance for method chaining.
         */
        public Builder max(float max) {
            this.max = max;
            return getThis();
        }

        /**
         * Sets the precision for the Float argument.
         * This determines how many decimal places will be retained when formatting the Float value.
         *
         * @param precision The number of decimal places to retain.
         * @return This Builder instance for method chaining.
         */
        public Builder precision(int precision) {
            this.precision = precision;
            return getThis();
        }

        /**
         * Creates a new FloatArgument instance with the specified properties.
         *
         * @return A new FloatArgument instance.
         */
        @Override
        public FloatArgument get() {
            return new FloatArgument(name, description, isRequired, min, max, precision);
        }
    }
}
