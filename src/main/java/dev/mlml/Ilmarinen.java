package dev.mlml;

import dev.mlml.command.CommandRegistry;
import dev.mlml.command.impl.*;
import dev.mlml.economy.IO;
import dev.mlml.handlers.Listener;
import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

public class Ilmarinen {
    @Getter
    private static JDA jda;
    public static final Logger logger = LoggerFactory.getLogger(Ilmarinen.class);

    public static void initCommandRegistry() {
        CommandRegistry.registerClass(Echo.class);
        CommandRegistry.registerClass(Help.class);
        CommandRegistry.registerClass(Daily.class);
        CommandRegistry.registerClass(UserInfo.class);
        CommandRegistry.registerClass(Coinflip.class);
        CommandRegistry.registerClass(Dog.class);
        CommandRegistry.registerClass(Slotmachine.class);
    }

    public static void main(String[] args) {
        if (Config.createConfigIfNotExist()) {
            logger.info("Config file created, please fill in the required fields");
            System.exit(0);
        }

        Config.loadFromFile();
        IO.load();

        String token = Config.BotConfig.getToken();

        if (token == null) {
            logger.error("Please specify a token as environment variable");
            System.exit(1);
        }

        initCommandRegistry();

        logger.debug("Initialized command registry");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            jda.shutdown();
            Config.saveToFile();
            IO.save();
        }));

        EnumSet<GatewayIntent> intents = EnumSet.of(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.MESSAGE_CONTENT
        );

        logger.info("Starting bot...");

        jda = JDABuilder.createLight(token, intents)
                        .addEventListeners(new Listener())
                        .build();
    }
}