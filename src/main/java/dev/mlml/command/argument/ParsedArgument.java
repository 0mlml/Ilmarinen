package dev.mlml.command.argument;

import lombok.Getter;

import java.util.Objects;

/**
 * Represents a parsed argument in a command.
 * Contains the argument definition and the parsed value.
 *
 * @param <V> The type of the value associated with the argument.
 */
public record ParsedArgument<V>(ArgumentBase<V> argument, V value) {
    /**
     * Checks if the argument is required and has a value.
     * @return true if the argument is required and has a value, false otherwise.
     */
    public boolean skip() {
        return Objects.isNull(value);
    }
}