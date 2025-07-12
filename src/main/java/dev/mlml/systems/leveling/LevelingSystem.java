package dev.mlml.systems.leveling;

import dev.mlml.Ilmarinen;
import dev.mlml.systems.IO;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * The LevelingSystem class manages the global leveling state, including guilds and users.
 * It provides methods to retrieve or create guilds and users, ensuring that they are initialized when accessed.
 * It also handles message processing for experience gain and level-ups.
 */
public class LevelingSystem {
    private static final Logger logger = LoggerFactory.getLogger(LevelingSystem.class);

    @Getter
    @Setter
    private static LevelingGlobal levelingGlobal = new LevelingGlobal();
    @Getter
    private static final Map<String, LevelingGuild> guilds = new HashMap<>();
    @Getter
    private static final Map<String, LevelingUser> users = new HashMap<>();

    /**
     * Retrieves a guild by its ID, creating it if it does not exist.
     *
     * @param id The ID of the guild.
     * @return The LevelingGuild object associated with the given ID.
     */
    public static LevelingGuild getGuild(String id) {
        if (!guilds.containsKey(id)) {
            guilds.put(id, new LevelingGuild(id));
        }

        return guilds.get(id);
    }

    /**
     * Retrieves a user by their ID, creating it if it does not exist.
     *
     * @param id The ID of the user.
     * @return The LevelingUser object associated with the given ID.
     */
    public static LevelingUser getUser(String id) {
        if (!users.containsKey(id)) {
            users.put(id, new LevelingUser(id));
        }

        return users.get(id);
    }

    /**
     * Puts a user into the leveling system, replacing any existing user with the same ID.
     *
     * @param id   The ID of the user.
     * @param user The LevelingUser object to be added or updated.
     */
    public static void putUser(String id, LevelingUser user) {
        users.put(id, user);
    }

    /**
     * Puts a guild into the leveling system, replacing any existing guild with the same ID.
     *
     * @param id     The ID of the guild.
     * @param guild  The LevelingGuild object to be added or updated.
     */
    public static void putGuild(String id, LevelingGuild guild) {
        guilds.put(id, guild);
    }

    /**
     * Processes a message for leveling purposes.
     * Checks if leveling is enabled, applies cooldowns, and awards experience.
     *
     * @param message The message to process.
     * @return true if experience was awarded, false otherwise.
     */
    public static boolean processMessage(Message message) {
        if (message.getChannelType() != ChannelType.TEXT) {
            return false;
        }

        String guildId = message.getGuild().getId();
        String userId = message.getAuthor().getId();
        String channelId = message.getChannel().getId();

        LevelingGuild guild = getGuild(guildId);
        LevelingUser user = getUser(userId);

        if (!levelingGlobal.isGlobalLevelingEnabled() || !guild.isLevelingEnabled()) {
            return false;
        }

        if (guild.isChannelExcluded(channelId)) {
            return false;
        }

        Member member = message.getMember();
        if (member != null) {
            for (var role : member.getRoles()) {
                if (guild.isRoleExcluded(role.getId())) {
                    return false;
                }
            }
        }

        long cooldownMs = guild.getMessageCooldownMs();
        long baseExp = guild.calculateExperienceForMessage();

        long userLevel = user.getLevel();
        long experienceAwarded = user.recordMessage(cooldownMs, baseExp);

        if (experienceAwarded > 0) {
            levelingGlobal.incrementTotalMessages();
            levelingGlobal.addTotalExperience(experienceAwarded);

            if (user.getLevel() - userLevel > 0) {
                handleLevelUp(message, guild, user);
            }

            user.updateStreak();

            logger.debug("Awarded {} experience to user {} in guild {}", experienceAwarded, userId, guildId);

            IO.getSystem(LevelingIO.class).save();
        }

        return experienceAwarded > 0;
    }

    /**
     * Handles a level-up event for a user.
     * Sends announcements and updates global statistics.
     *
     * @param message The message that triggered the level-up.
     * @param guild   The guild where the level-up occurred.
     * @param user    The user who leveled up.
     */
    private static void handleLevelUp(Message message, LevelingGuild guild, LevelingUser user) {
        levelingGlobal.incrementTotalLevelUps();

        if (guild.isLevelUpAnnouncements() && guild.getLevelUpChannelId() != null) {
            String announcement = guild.getFormattedLevelUpMessage(
                    message.getAuthor().getAsMention(),
                    user.getLevel()
            );

            try {
                var channel = Ilmarinen.getJda().getTextChannelById(guild.getLevelUpChannelId());
                if (channel != null) {
                    channel.sendMessage(announcement).queue();
                    logger.info("Sent level-up announcement for user {} in guild {}", 
                              message.getAuthor().getId(), message.getGuild().getId());
                }
            } catch (Exception e) {
                logger.error("Failed to send level-up announcement", e);
            }
        }

        logger.info("User {} leveled up to level {} in guild {}", 
                   message.getAuthor().getId(), user.getLevel(), message.getGuild().getId());
    }

    /**
     * Manually awards experience to a user.
     *
     * @param userId The ID of the user.
     * @param amount The amount of experience to award.
     * @return true if the user leveled up, false otherwise.
     */
    public static boolean awardExperience(String userId, long amount) {
        LevelingUser user = getUser(userId);
        boolean leveledUp = user.addExperience(amount);
        
        if (leveledUp) {
            levelingGlobal.incrementTotalLevelUps();
        }
        
        levelingGlobal.addTotalExperience(amount);
        return leveledUp;
    }

    /**
     * Gets the top users by level across all guilds.
     *
     * @param limit The maximum number of users to return.
     * @return A map of user IDs to their levels, sorted by level (highest first).
     */
    public static Map<String, Integer> getTopUsers(int limit) {
        return users.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().getLevel(), e1.getValue().getLevel()))
                .limit(limit)
                .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue().getLevel()), HashMap::putAll);
    }

    /**
     * Gets the top users by experience across all guilds.
     *
     * @param limit The maximum number of users to return.
     * @return A map of user IDs to their experience, sorted by experience (highest first).
     */
    public static Map<String, Long> getTopUsersByExperience(int limit) {
        return users.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().getExperience(), e1.getValue().getExperience()))
                .limit(limit)
                .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue().getExperience()), HashMap::putAll);
    }
} 