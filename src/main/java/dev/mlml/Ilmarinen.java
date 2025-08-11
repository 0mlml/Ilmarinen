package dev.mlml;

import dev.mlml.command.Command;
import dev.mlml.command.CommandRegistry;
import dev.mlml.command.impl.*;
import dev.mlml.systems.IO;
import dev.mlml.handlers.EventManager;
import dev.mlml.systems.economy.EconIO;
import dev.mlml.systems.giveaway.GiveawayIO;
import dev.mlml.systems.leveling.LevelingIO;
import dev.mlml.systems.starboard.StarboardIO;
import dev.mlml.systems.music.MusicSystem;
import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.managers.Presence;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;

public class Ilmarinen {
    @Getter
    private static JDA jda;
    @Getter
    private static MusicSystem musicSystem;
    public static final Logger logger = LoggerFactory.getLogger(Ilmarinen.class);

    public static void initCommandRegistry() {
        CommandRegistry.registerClass(Echo.class);
        CommandRegistry.registerClass(Help.class);
        CommandRegistry.registerClass(Daily.class);
        CommandRegistry.registerClass(UserInfo.class);
        CommandRegistry.registerClass(Coinflip.class);
        CommandRegistry.registerClass(Dog.class);
        CommandRegistry.registerClass(Slotmachine.class);
        CommandRegistry.registerClass(Crash.class);
        CommandRegistry.registerClass(Leaderboard.class);
        CommandRegistry.registerClass(Bankruptcy.class);
        CommandRegistry.registerClass(Adjust.class);
        CommandRegistry.registerClass(Pay.class);
        CommandRegistry.registerClass(Blackjack.class);
        CommandRegistry.registerClass(RideTheBus.class);
        CommandRegistry.registerClass(Summary.class);
        CommandRegistry.registerClass(CrossyRoad.class);
        CommandRegistry.registerClass(Starboard.class);
        CommandRegistry.registerClass(Duck.class);
        CommandRegistry.registerClass(Fox.class);
        CommandRegistry.registerClass(Giveaway.class);
        CommandRegistry.registerClass(LevelLeaderboard.class);
        CommandRegistry.registerClass(LevelConfig.class);
        CommandRegistry.registerClass(Music.class);
        CommandRegistry.registerClass(Baccarat.class);
    }


    private static List<Activity> status = List.of(Activity.playing("among the stars"),
                                                   Activity.watching("over the horizon")
    );

    private static void presenceThread() {
        Presence presence = jda.getPresence();
        int i = 0;
        while (true) {
            try {
                presence.setActivity(status.get(i));
                i = (i + 1) % status.size();
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                logger.error("Presence thread interrupted", e);
                break;
            }
        }
    }

    public static void main(String[] args) {
        if (Config.createConfigIfNotExist()) {
            logger.info("Config file created, please fill in the required fields");
            System.exit(0);
        }

        Config.loadFromFile();
        EconIO econIO = new EconIO();
        StarboardIO starboardIO = new StarboardIO();
        GiveawayIO giveawayIO = new GiveawayIO();
        LevelingIO levelingIO = new LevelingIO();
        musicSystem = new MusicSystem();

        IO.loadAll();

        String token = Config.getBotConfig().getToken();

        if (token == null) {
            logger.error("Please specify a token as environment variable");
            System.exit(1);
        }

        initCommandRegistry();

        logger.debug("Initialized command registry");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            jda.shutdown();
            Config.saveToFile();
            IO.saveAll();
        }));

        EnumSet<GatewayIntent> intents = EnumSet.of(GatewayIntent.GUILD_MESSAGES,
                                                    GatewayIntent.GUILD_MEMBERS,
                                                    GatewayIntent.MESSAGE_CONTENT,
                                                    GatewayIntent.GUILD_MESSAGE_REACTIONS,
                                                    GatewayIntent.GUILD_VOICE_STATES
        );

        logger.info("Starting bot...");

        jda = JDABuilder.create(token, intents).addEventListeners(new EventManager()).build();

        String version = Utils.getVersion();

        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        if (version.contains("-dev")) {
            rootLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
            jda.getPresence().setActivity(net.dv8tion.jda.api.entities.Activity.playing("v" + version));
        } else {
            rootLogger.setLevel(ch.qos.logback.classic.Level.INFO);
            Thread.startVirtualThread(Ilmarinen::presenceThread);
        }

        logger.info("Hello! I am: {}", jda.getSelfUser().getName());
    }
}