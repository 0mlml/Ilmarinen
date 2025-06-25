package dev.mlml.command.argument;

import lombok.Getter;

/**
 * Represents a string argument in a command.
 * This class is used to define string arguments that can be required or optional,
 * and can also be variadic (accepting multiple values).
 */
@Getter
public class StringArgument extends ArgumentBase<String> {
    final boolean isVArgs;

    public StringArgument(String name, String description, boolean isRequired, boolean isVArgs) {
        super(name, description, isRequired);

        this.isVArgs = isVArgs;
    }

    /**
     * Parses the input string and returns it as is.
     * This method does not perform any validation or transformation on the input.
     *
     * @param input The input string to parse.
     * @return The input string itself.
     */
    @Override
    public String parse(String input) {
        return input;
    }

    /**
     * Builder for creating instances of StringArgument.
     * Allows setting the name, description, whether the argument is required,
     * and whether it is variadic (accepting multiple values).
     */
    public static class Builder extends ArgumentBase.Builder<StringArgument.Builder, String, StringArgument> {
        boolean isVArgs = false;

        /**
         * Constructs a new Builder with the specified name.
         *
         * @param name The name of the string argument.
         */
        public Builder(String name) {
            super(name);
        }

        /**
         * Sets the argument as variadic (accepting multiple values).
         * @return This Builder instance for method chaining.
         */
        public Builder isVArgs() {
            this.isVArgs = true;
            return getThis();
        }

        /**
         * Creates a new StringArgument instance with the specified properties.
         *
         * @return A new StringArgument instance.
         */
        @Override
        public StringArgument get() {
            return new StringArgument(name, description, isRequired, isVArgs);
        }
    }
}
