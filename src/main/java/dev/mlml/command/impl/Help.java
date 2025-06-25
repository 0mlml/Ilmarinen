package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.CommandRegistry;
import dev.mlml.command.Context;
import dev.mlml.command.SubcommandCommand;
import dev.mlml.command.argument.ArgumentBase;
import dev.mlml.command.argument.StringArgument;
import dev.mlml.command.argument.SubcommandArgument;
import net.dv8tion.jda.api.Permission;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@CommandInfo(
        keywords = {"help"},
        name = "Help",
        description = "Get help",
        permissions = {Permission.MESSAGE_SEND},
        category = CommandInfo.Category.Util
)
public class Help extends Command {
    private static final StringArgument COMMAND_ARG = new StringArgument.Builder("command")
            .description("Command to get details on")
            .get();

    private static final StringArgument SUBCOMMAND_ARG = new StringArgument.Builder("subcommand")
            .description("Subcommand to get details on")
            .get();

    public Help() {
        super(COMMAND_ARG, SUBCOMMAND_ARG);
    }

    @Override
    public void execute(Context ctx) {
        String commandName = ctx.getArgument(COMMAND_ARG).map(arg -> arg.value().toLowerCase()).orElse(null);
        String subcommandName = ctx.getArgument(SUBCOMMAND_ARG).map(arg -> arg.value().toLowerCase()).orElse(null);

        Command command = CommandRegistry.getCommandByKeyword(commandName);

        if (Objects.isNull(commandName)) {
            showAllCommands(ctx);
            return;
        }

        if (Objects.isNull(command)) {
            ctx.fail("Unknown command: " + commandName);
            return;
        }

        // If a subcommand is specified, show subcommand help
        if (subcommandName != null && command instanceof SubcommandCommand) {
            showSubcommandHelp(ctx, (SubcommandCommand) command, commandName, subcommandName);
            return;
        }

        // Show main command help
        showCommandHelp(ctx, command, commandName);
    }

    private void showAllCommands(Context ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append("Here's a list of all the commands:\n");
        sb.append(String.format("You can send `%shelp [command name]` to get info on a specific command!\n",
                                ctx.getPrefix()));
        sb.append(String.format("For subcommands, use `%shelp [command name] [subcommand]`!\n\n",
                                ctx.getPrefix()));

        for (CommandInfo.Category category : CommandInfo.Category.values()) {
            boolean isCategoryPrinted = false;
            StringBuilder categoryCommands = new StringBuilder();

            for (Command commandObj : CommandRegistry.getCommands()) {
                if (commandObj.getCategory().equals(category)) {
                    if (!isCategoryPrinted) {
                        categoryCommands.append(category).append(":\n");
                        isCategoryPrinted = true;
                    }

                    categoryCommands.append(commandObj.getKeywords()[0]);

                    // Show if command has subcommands
                    if (commandObj instanceof SubcommandCommand) {
                        categoryCommands.append(" (has subcommands)");
                    }

                    categoryCommands.append(", ");
                }
            }

            if (isCategoryPrinted) {
                // Remove trailing ", "
                categoryCommands.setLength(categoryCommands.length() - 2);
                categoryCommands.append("\n\n");
                sb.append(categoryCommands);
            }
        }

        ctx.succeed(sb.toString());
    }

    private void showCommandHelp(Context ctx, Command command, String commandName) {
        StringBuilder sb = new StringBuilder();

        sb.append(command.getName()).append(": ").append(command.getDescription()).append("\n");
        sb.append("Usage: ").append(ctx.getPrefix()).append(commandName).append(" ").append(command.getUsage());
        sb.append("\n\n");

        if (!command.getArguments().isEmpty()) {
            sb.append("Arguments:\n").append(command.getArgDescription());
        }

        // If this is a subcommand command, show available subcommands
        if (command instanceof SubcommandCommand) {
            SubcommandArgument subcommandArg = getSubcommandArgument(command);
            if (subcommandArg != null) {
                sb.append("\n\nAvailable subcommands:\n");
                Set<String> subcommands = subcommandArg.getValidSubcommands();

                for (String subcommand : subcommands) {
                    sb.append("- ").append(subcommand);

                    List<ArgumentBase<?>> subArgs = subcommandArg.getArgumentsForSubcommand(subcommand);
                    if (!subArgs.isEmpty()) {
                        sb.append(" ");
                        for (ArgumentBase<?> arg : subArgs) {
                            if (arg.isRequired()) {
                                sb.append("<").append(arg.getName()).append("> ");
                            } else {
                                sb.append("[").append(arg.getName()).append("] ");
                            }
                        }
                    }
                    sb.append("\n");
                }

                sb.append("\nUse `").append(ctx.getPrefix()).append("help ").append(commandName)
                  .append(" [subcommand]` for detailed help on a specific subcommand.");
            }
        }

        ctx.succeed(sb.toString());
    }

    private void showSubcommandHelp(Context ctx, SubcommandCommand command, String commandName, String subcommandName) {
        SubcommandArgument subcommandArg = getSubcommandArgument(command);

        if (subcommandArg == null) {
            ctx.fail("This command doesn't have subcommands");
            return;
        }

        Set<String> validSubcommands = subcommandArg.getValidSubcommands();
        if (!validSubcommands.contains(subcommandName)) {
            ctx.fail("Unknown subcommand: " + subcommandName +
                             ". Available: " + String.join(", ", validSubcommands));
            return;
        }

        List<ArgumentBase<?>> subcommandArgs = subcommandArg.getArgumentsForSubcommand(subcommandName);

        StringBuilder sb = new StringBuilder();

        sb.append(command.getName()).append(" - ").append(subcommandName).append(" subcommand\n");
        sb.append("Usage: ").append(ctx.getPrefix()).append(commandName).append(" ").append(subcommandName);

        for (ArgumentBase<?> arg : subcommandArgs) {
            sb.append(" ");
            if (arg.isRequired()) {
                sb.append("<").append(arg.getName()).append(">");
            } else {
                sb.append("[").append(arg.getName()).append("]");
            }
        }

        sb.append("\n");

        if (!subcommandArgs.isEmpty()) {
            sb.append("\nArguments:\n");
            for (ArgumentBase<?> arg : subcommandArgs) {
                sb.append("- ").append(arg.getName());
                if (arg.isRequired()) {
                    sb.append(" (required)");
                } else {
                    sb.append(" (optional)");
                }
                if (!arg.getDescription().isEmpty()) {
                    sb.append(": ").append(arg.getDescription());
                }
                sb.append("\n");
            }
        }

        ctx.succeed(sb.toString());
    }

    private SubcommandArgument getSubcommandArgument(Command command) {
        for (ArgumentBase<?> arg : command.getArguments()) {
            if (arg instanceof SubcommandArgument) {
                return (SubcommandArgument) arg;
            }
        }
        return null;
    }
}