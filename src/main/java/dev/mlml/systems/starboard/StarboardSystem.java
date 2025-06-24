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

public class StarboardSystem {
    private static final Logger logger = LoggerFactory.getLogger(StarboardSystem.class);

    @Getter
    private static final Map<String, StarboardGuild> guilds = new HashMap<>();

    public static StarboardGuild getGuild(String id) {
        if (!guilds.containsKey(id)) {
            guilds.put(id, new StarboardGuild(id));
        }
        return guilds.get(id);
    }

    public static void onMessageReactionAdd(MessageReactionAddEvent event) {
        // Don't process bot reactions
        if (event.getUser() == null || event.getUser().isBot()) {
            return;
        }

        StarboardGuild sbg = getGuild(event.getGuild().getId());

        // Check if starboard is configured
        if (sbg.getChannel() == null) {
            return;
        }

        // Check if this is the starboard emoji
        String reactionEmoji = getEmojiName(event.getReaction());
        if (!reactionEmoji.equals(sbg.getEmoji())) {
            return;
        }

        // Retrieve the message and process starboard logic
        event.getChannel().retrieveMessageById(event.getMessageId()).queue(
                message -> processStarboardReaction(message, sbg),
                throwable -> logger.error("Failed to retrieve message for starboard processing", throwable)
        );
    }

    public static void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        // Don't process bot reactions
        if (event.getUser() == null || event.getUser().isBot()) {
            return;
        }

        StarboardGuild sbg = getGuild(event.getGuild().getId());

        // Check if starboard is configured
        if (sbg.getChannel() == null) {
            return;
        }

        // Check if this is the starboard emoji
        String reactionEmoji = getEmojiName(event.getReaction());
        if (!reactionEmoji.equals(sbg.getEmoji())) {
            return;
        }

        // Retrieve the message and process starboard logic
        event.getChannel().retrieveMessageById(event.getMessageId()).queue(
                message -> processStarboardReaction(message, sbg),
                throwable -> logger.error("Failed to retrieve message for starboard processing", throwable)
        );
    }

    private static void processStarboardReaction(Message message, StarboardGuild sbg) {
        // Don't starboard messages from the starboard channel itself
        if (message.getChannel().getId().equals(sbg.getChannelId())) {
            return;
        }

        // Find the starboard reaction and count
        int starCount = 0;
        for (MessageReaction reaction : message.getReactions()) {
            String reactionEmoji = getEmojiName(reaction);
            if (reactionEmoji.equals(sbg.getEmoji())) {
                starCount = reaction.getCount();
                break;
            }
        }

        logger.debug("Processing starboard reaction: {} stars, threshold: {}", starCount, sbg.getThreshold());

        // Check if message meets threshold
        if (starCount >= sbg.getThreshold()) {
            sbg.sendStarboardMessage(message, starCount);
        } else if (sbg.isAlreadyStarred(message)) {
            // Update existing starboard message (might get deleted if below threshold)
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

    public static void setChannel(String guildId, String channelId) {
        StarboardGuild sbg = getGuild(guildId);
        sbg.setChannelId(channelId);
        logger.info("Set starboard channel for guild {} to {}", guildId, channelId);
        IO.getSystem(StarboardIO.class).save();
    }

    public static void setThreshold(String guildId, int threshold) {
        StarboardGuild sbg = getGuild(guildId);
        sbg.setThreshold(threshold);
        logger.info("Set starboard threshold for guild {} to {}", guildId, threshold);
        IO.getSystem(StarboardIO.class).save();
    }

    public static void setEmoji(String guildId, String emoji) {
        StarboardGuild sbg = getGuild(guildId);
        sbg.setEmoji(emoji);
        logger.info("Set starboard emoji for guild {} to {}", guildId, emoji);
        IO.getSystem(StarboardIO.class).save();
    }

    public static StarboardGuild.StarboardConfig getConfig(String guildId) {
        StarboardGuild sbg = getGuild(guildId);
        return sbg.getConfig();
    }

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