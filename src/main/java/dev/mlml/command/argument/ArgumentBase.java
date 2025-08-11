package dev.mlml.command.argument;

import lombok.Getter;

/**
 * Base class for command arguments.
 * Provides common functionality for parsing and formatting argument information.
 *
 * @param <V> The type of value this argument will parse.
 */
@Getter
public abstract class ArgumentBase<V> {
    final String name, description;
    final boolean isRequired;

    public ArgumentBase(String name, String description, boolean required) {
        this.name = name;
        this.description = description;
        this.isRequired = required;
    }

    /**
     * Parses the input string into a value of type V.
     * This method should be implemented by subclasses to provide specific parsing logic.
     *
     * @param input The input string to parse.
     * @return The parsed value of type V.
     */
    public abstract V parse(String input) throws IllegalArgumentException;

    /**
     * Returns a help description for this argument, including its name, whether it is required,
     * and its description if provided.
     *
     * @return A formatted string describing the argument.
     */
    public String getHelpDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("- ").append(name);

        if (isRequired) {
            sb.append(" (required)");
        } else {
            sb.append(" (optional)");
        }

        if (!description.isEmpty()) {
            sb.append(": ").append(description);
        }

        return sb.toString();
    }

    /**
     * Returns the usage format for this argument.
     * If the argument is required, it will be enclosed in angle brackets.
     * If it is optional, it will be enclosed in square brackets.
     *
     * @return A string representing the usage format of the argument.
     */
    public String getUsageFormat() {
        if (isRequired) {
            return "<" + name + ">";
        } else {
            return "[" + name + "]";
        }
    }

    /**
     * Builder class for creating instances of ArgumentBase.
     * Provides methods to set the name, description, and required status of the argument.
     *
     * @param <B> The type of the builder itself, for method chaining.
     * @param <V> The type of value this argument will parse.
     * @param <S> The type of ArgumentBase being built.
     */
    @SuppressWarnings("unchecked")
    public abstract static class Builder<B extends Builder<?, ?, ?>, V, S extends ArgumentBase<?>> {
        String name, description = "";
        boolean isRequired = false;

        /**
         * Constructor for the builder.
         * Initializes the name of the argument.
         *
         * @param name The name of the argument to be built.
         */
        protected Builder(String name) {
            this.name = name;
        }

        /**
         * Sets the name of the argument.
         *
         * @param name The name to set for the argument.
         * @return The builder instance for method chaining.
         */
        public B name(String name) {
            this.name = name;
            return getThis();
        }

        /**
         * Sets the description of the argument.
         *
         * @param description The description to set for the argument.
         * @return The builder instance for method chaining.
         */
        public B description(String description) {
            this.description = description;
            return getThis();
        }

        /**
         * Marks the argument as required.
         *
         * @return The builder instance for method chaining.
         */
        public B require() {
            this.isRequired = true;
            return getThis();
        }

        /**
         * Creates an instance of the ArgumentBase subclass being built
         * with the specified properties.
         * @return The new instance of the ArgumentBase subclass.
         */
        public abstract S get();

        /**
         * Returns the current instance of the builder.
         * This method is used to allow method chaining in subclasses.
         *
         * @return The current instance of the builder.
         */
        protected B getThis() {
            return (B) this;
        }
    }
}