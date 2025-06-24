package dev.mlml.command;

import dev.mlml.command.argument.ArgumentBase;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.command.argument.ParsedArgumentList;
import lombok.Getter;

public record SubcommandContext(Context baseContext, String subcommand, ParsedArgumentList subcommandArguments) {

    public <T> ParsedArgument<T> getSubcommandArgument(ArgumentBase<T> argument) {
        return subcommandArguments.getArguments()
                                  .stream()
                                  .filter(arg -> arg.getArgument().equals(argument))
                                  .findFirst()
                                  .map(arg -> (ParsedArgument<T>) arg)
                                  .orElse(null);
    }

    public ParsedArgument<?> getSubcommandArgument(int index) {
        if (index >= subcommandArguments.getArguments().size()) {
            return null;
        }
        return subcommandArguments.getArguments()
                .get(index);
    }

    public void reply(String message) {
        baseContext.reply(message);
    }

}