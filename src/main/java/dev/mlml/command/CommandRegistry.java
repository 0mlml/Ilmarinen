package dev.mlml.command;

import dev.mlml.Utils;
import lombok.Getter;
import lombok.SneakyThrows;
import net.dv8tion.jda.api.entities.Message;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class CommandRegistry {
    private static final Logger logger = LoggerFactory.getLogger(CommandRegistry.class);

    @Getter
    private static final Set<Command> commands = new HashSet<>();
    private static final Map<Command, Map<String, Long>> cooldowns = new HashMap<>();

    private static long getCooldown(Command command, String userId) {
        if (!cooldowns.containsKey(command)) {
            cooldowns.put(command, new HashMap<>());
        }

        Map<String, Long> cooldownMap = cooldowns.get(command);
        if (!cooldownMap.containsKey(userId)) {
            return 0;
        }

        if (System.currentTimeMillis() - cooldownMap.get(userId) > command.getCooldown() * 1000L) {
            cooldownMap.remove(userId);
            return 0;
        }

        return command.getCooldown() * 1000L - (System.currentTimeMillis() - cooldownMap.get(userId));
    }

    private static void setCooldown(Command command, String userId) {
        if (!cooldowns.containsKey(command)) {
            cooldowns.put(command, new HashMap<>());
        }

        cooldowns.get(command).put(userId, System.currentTimeMillis());
    }

    public static void registerClass(Class<? extends Command> commandClass) {
        if (commands.stream().anyMatch(command -> command.getClass().equals(commandClass))) {
            logger.error("Command class already registered: {}", commandClass.getName());
            return;
        }

        Command instance;
        try {
            instance = commandClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.error("Failed to register command class: {}, {}", commandClass.getName(), e.getMessage());
            return;
        }

        commands.add(instance);
    }

    @Nullable
    public static Command getCommandByKeyword(String name) {
        return commands.stream()
                       .filter(command -> Arrays.asList(command.getKeywords()).contains(name))
                       .findFirst()
                       .orElse(null);
    }

    @SneakyThrows
    public static void executeCommand(Message message) {
        if (message.getAuthor().isBot()) {
            return;
        }

        Context ctx = new Context(message);

        if (!ctx.isValidCommand) {
            return;
        }

        Command command = getCommandByKeyword(ctx.command);
        if (Objects.isNull(command)) {
            message.reply("Command not found!").complete();
            return;
        }

        if (!command.canExecute(ctx.member, ctx.gChannel)) {
            message.reply("You don't have permission to execute this command!").complete();
            return;
        }

        long cooldown = getCooldown(command, ctx.getMember().getId());
        if (cooldown > 0) {
            String prettyPrint = Utils.timePrettyPrint(cooldown);

            String cooldownReply = String.format("You must wait %s before using this command again!", prettyPrint);

            message.reply(cooldownReply)
                   .complete();
            return;
        }

        boolean parseError;
        try {
            parseError = ctx.parse(command);
        } catch (NumberFormatException e) {
            logger.error("Failed to parse command: {}, {}", command.getName(), e.getMessage());
            message.reply(String.format("Invalid argument type!\nUsage: %s%s %s",
                                        ctx.getPrefix(),
                                        command.getKeywords()[0],
                                        command.getUsage()
            )).complete();
            return;
        } catch (Exception e) {
            logger.error("Failed to parse command: {}, {}", command.getName(), e.getMessage());
            message.reply("An error occurred while parsing the command!").complete();
            return;
        }

        if (parseError) {
            message.reply(String.format("Usage: %s%s %s",
                                        ctx.getPrefix(),
                                        command.getKeywords()[0],
                                        command.getUsage()
            )).complete();
            return;
        }

        setCooldown(command, ctx.getMember().getId());

        logger.debug("Executing command: {}", command.getName());
        command.execute(ctx);
    }
}
