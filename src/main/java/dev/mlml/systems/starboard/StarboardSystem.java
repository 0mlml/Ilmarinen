package dev.mlml.systems.starboard;

import dev.mlml.Ilmarinen;
import dev.mlml.systems.IO;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * StarboardSystem handles starboard functionality for guilds.
 * It manages starboard configurations, processes reactions, and sends starboard messages.
 */
public class StarboardSystem {
    private static final Logger logger = LoggerFactory.getLogger(StarboardSystem.class);

    @Getter
    private static final Map<String, StarboardGuild> guilds = new HashMap<>();

    /**
     * Retrieves or creates a StarboardGuild instance for the given guild ID.
     *
     * @param id the ID of the guild
     * @return the StarboardGuild instance for the guild
     */
    public static StarboardGuild getGuild(String id) {
        if (!guilds.containsKey(id)) {
            guilds.put(id, new StarboardGuild(id));
        }
        return guilds.get(id);
    }

    /**
     * Initializes the StarboardSystem by loading existing guild configurations.
     */
    public static void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) {
            return;
        }

        StarboardGuild sbg = getGuild(event.getGuild().getId());

        if (sbg.getChannel() == null) {
            return;
        }

        String reactionEmoji = getEmojiName(event.getReaction());
        if (!reactionEmoji.equals(sbg.getEmoji())) {
            return;
        }

        event.getChannel().retrieveMessageById(event.getMessageId()).queue(
                message -> processStarboardReaction(message, sbg),
                throwable -> logger.error("Failed to retrieve message for starboard processing", throwable)
        );
    }

    /**
     * Handles the removal of a reaction on a message.
     * If the reaction is the starboard emoji, it processes the starboard logic.
     *
     * @param event the MessageReactionRemoveEvent
     */
    public static void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) {
            return;
        }

        StarboardGuild sbg = getGuild(event.getGuild().getId());

        if (sbg.getChannel() == null) {
            return;
        }

        String reactionEmoji = getEmojiName(event.getReaction());
        if (!reactionEmoji.equals(sbg.getEmoji())) {
            return;
        }

        event.getChannel().retrieveMessageById(event.getMessageId()).queue(
                message -> processStarboardReaction(message, sbg),
                throwable -> logger.error("Failed to retrieve message for starboard processing", throwable)
        );
    }

    private static void processStarboardReaction(Message message, StarboardGuild sbg) {
        if (message.getChannel().getId().equals(sbg.getChannelId())) {
            return;
        }

        int starCount = 0;
        for (MessageReaction reaction : message.getReactions()) {
            String reactionEmoji = getEmojiName(reaction);
            if (reactionEmoji.equals(sbg.getEmoji())) {
                starCount = reaction.getCount();
                break;
            }
        }

        logger.debug("Processing starboard reaction: {} stars, threshold: {}", starCount, sbg.getThreshold());

        if (starCount >= sbg.getThreshold()) {
            sbg.sendStarboardMessage(message, starCount);
        } else if (sbg.isAlreadyStarred(message)) {
            sbg.updateStarboardMessage(message, starCount);
        }
    }

    private static String getEmojiName(MessageReaction reaction) {
        if (reaction.getEmoji().getType() == Emoji.Type.UNICODE) {
            return reaction.getEmoji().getName();
        } else {
            return "<:" + reaction.getEmoji().getName() + ":" + reaction.getEmoji().asCustom().getId() + ">";
        }
    }

    /**
     * Sets the starboard channel for a guild.
     * @param guildId the ID of the guild
     * @param channelId the ID of the channel to set as the starboard channel
     */
    public static void setChannel(String guildId, String channelId) {
        StarboardGuild sbg = getGuild(guildId);
        sbg.setChannelId(channelId);
        logger.info("Set starboard channel for guild {} to {}", guildId, channelId);
        IO.getSystem(StarboardIO.class).save();
    }

    /**
     * Sets the starboard threshold for a guild.
     * @param guildId the ID of the guild
     * @param threshold the number of stars required to send a message to the starboard
     */
    public static void setThreshold(String guildId, int threshold) {
        StarboardGuild sbg = getGuild(guildId);
        sbg.setThreshold(threshold);
        logger.info("Set starboard threshold for guild {} to {}", guildId, threshold);
        IO.getSystem(StarboardIO.class).save();
    }

    /**
     * Sets the starboard emoji for a guild.
     * @param guildId the ID of the guild
     * @param emoji the emoji to use for starboard reactions
     */
    public static void setEmoji(String guildId, String emoji) {
        StarboardGuild sbg = getGuild(guildId);
        sbg.setEmoji(emoji);
        logger.info("Set starboard emoji for guild {} to {}", guildId, emoji);
        IO.getSystem(StarboardIO.class).save();
    }

    /**
     * Retrieves the starboard configuration for a guild.
     * @param guildId the ID of the guild
     * @return the StarboardConfig for the guild
     */
    public static StarboardGuild.StarboardConfig getConfig(String guildId) {
        StarboardGuild sbg = getGuild(guildId);
        return sbg.getConfig();
    }

    /**
     * Checks if a channel is valid for starboard operations.
     * A channel is considered valid if it exists in the guild and is a text channel.
     *
     * @param guildId the ID of the guild
     * @param channelId the ID of the channel to check
     * @return true if the channel is valid, false otherwise
     */
    public static boolean isValidChannel(String guildId, String channelId) {
        try {
            var guild = Ilmarinen.getJda().getGuildById(guildId);
            if (guild == null) {
                return false;
            }
            return guild.getTextChannelById(channelId) != null;
        } catch (Exception e) {
            return false;
        }
    }
}