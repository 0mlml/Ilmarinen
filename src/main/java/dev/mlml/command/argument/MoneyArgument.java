package dev.mlml.command.argument;

import lombok.Getter;

/**
 * Represents an argument for monetary values in commands.
 */
@Getter
public class MoneyArgument extends ArgumentBase<Float> {
    public MoneyArgument(String name, String description, boolean isRequired) {
        super(name, description, isRequired);
    }

    /**
     * Parses the input string to a Float value representing money.
     * If the input is "all", it returns Float.MAX_VALUE.
     *
     * @param input The input string to parse.
     * @return The parsed Float value, or Float.MAX_VALUE if the input is "all".
     */
    @Override
    public Float parse(String input) {
        if (input.equalsIgnoreCase("all")) {
            return Float.MAX_VALUE;
        }
        return Float.parseFloat(input);
    }

    /**
     * Builder for creating instances of MoneyArgument.
     */
    public static class Builder extends ArgumentBase.Builder<MoneyArgument.Builder, Float, MoneyArgument> {
        /**
         * Constructs a new Builder with the specified name.
         *
         * @param name The name of the Money argument.
         */
        public Builder(String name) {
            super(name);
        }

        /**
         * Creates a new MoneyArgument instance with the specified properties.
         *
         * @return A new MoneyArgument instance.
         */
        @Override
        public MoneyArgument get() {
            return new MoneyArgument(name, description, isRequired);
        }
    }
}
