package dev.mlml.command;

import dev.mlml.command.argument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Represents a command that has subcommands.
 * Subcommands are specified using a SubcommandArgument.
 */
public abstract class SubcommandCommand extends Command {
    private static final Logger logger = LoggerFactory.getLogger(SubcommandCommand.class);

    private final SubcommandArgument subcommandArgument;

    public SubcommandCommand(SubcommandArgument subcommandArgument, ArgumentBase<?>... additionalArgs) {
        super(combineArgs(subcommandArgument, additionalArgs));
        this.subcommandArgument = subcommandArgument;
    }

    private static ArgumentBase<?>[] combineArgs(SubcommandArgument subcommandArg, ArgumentBase<?>... additionalArgs) {
        ArgumentBase<?>[] combined = new ArgumentBase<?>[additionalArgs.length + 1];
        combined[0] = subcommandArg;
        System.arraycopy(additionalArgs, 0, combined, 1, additionalArgs.length);
        return combined;
    }

    @Override
    public final void execute(Context ctx) {
        String subcommand = ctx.getArgument(subcommandArgument).map(ParsedArgument::value).orElse(null);

        if (subcommand == null) {
            ctx.reply("Please specify a subcommand. Available: " + String.join(", ",
                                                                               subcommandArgument.getValidSubcommands()
            ));
            return;
        }

        List<ArgumentBase<?>> subcommandArgs = subcommandArgument.getArgumentsForSubcommand(subcommand);
        ParsedArgumentList parsedSubcommandArgs = parseSubcommandArguments(ctx, subcommandArgs);

        if (parsedSubcommandArgs == null) {
            return;
        }

        SubcommandContext subCtx = new SubcommandContext(ctx, subcommand, parsedSubcommandArgs);
        executeSubcommand(subCtx);
    }

    private ParsedArgumentList parseSubcommandArguments(Context ctx, List<ArgumentBase<?>> subcommandArgs) {
        ParsedArgumentList parsedArgs = new ParsedArgumentList();

        String[] rawArgs = ctx.getArgs();
        int argIndex = 1;

        for (int i = 0; i < subcommandArgs.size(); i++) {
            ArgumentBase<?> arg = subcommandArgs.get(i);

            if (arg instanceof StringArgument && ((StringArgument) arg).isVArgs()) {
                List<String> remainingArgs = List.of(rawArgs).subList(argIndex, rawArgs.length);
                if (remainingArgs.isEmpty()) {
                    return null;
                }
                parsedArgs.add(arg, String.join(" ", remainingArgs));
                break;
            }

            if (argIndex >= rawArgs.length) {
                if (arg.isRequired()) {
                    ctx.reply("Missing required argument: " + arg.getName());
                    return null;
                }
                parsedArgs.add(arg); // Add with null value
            } else {
                try {
                    parsedArgs.add(arg, rawArgs[argIndex]);
                    argIndex++;
                } catch (Exception e) {
                    ctx.reply("Invalid value for argument " + arg.getName() + ": " + e.getMessage());
                    return null;
                }
            }
        }

        return parsedArgs;
    }

    public abstract void executeSubcommand(SubcommandContext subCtx);
}