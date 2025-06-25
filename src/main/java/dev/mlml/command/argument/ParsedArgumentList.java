package dev.mlml.command.argument;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a list of parsed arguments in a command.
 * This class manages the collection of parsed arguments and their validity.
 */
public class ParsedArgumentList {
    private static final Logger logger = LoggerFactory.getLogger(ParsedArgumentList.class);

    @Getter
    private final List<ParsedArgument<?>> arguments = new ArrayList<>();
    private boolean isValid = true;

    /**
     * Sets the validity of the argument list.
     */
    public void invalidate() {
        this.isValid = false;
    }

    /**
     * Adds a parsed argument to the list.
     * @param argument the argument definition to be parsed
     * @param input the input string to parse the argument from
     * @return the parsed argument
     * @param <V> the type of the value associated with the argument
     */
    public <V> ParsedArgument<V> add(ArgumentBase<V> argument, String input) {
        V parsedValue = argument.parse(input);
        ParsedArgument<V> parsedArg = new ParsedArgument<>(argument, parsedValue);
        logger.debug("Parsed argument: {}", parsedArg);
        arguments.add(parsedArg);
        return parsedArg;
    }

    /**
     * Adds a parsed argument without an input value.
     * This is typically used for arguments that do not require parsing, such as flags.
     * @param argument the argument definition to be added
     * @return the parsed argument
     * @param <V> the type of the value associated with the argument
     */
    public <V> ParsedArgument<V> add(ArgumentBase<V> argument) {
        ParsedArgument<V> parsedArg = new ParsedArgument<>(argument, null);
        logger.debug("Filled argument: {}", parsedArg);
        arguments.add(parsedArg);
        return parsedArg;
    }


}
