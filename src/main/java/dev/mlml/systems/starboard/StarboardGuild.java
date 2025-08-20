package dev.mlml.systems.starboard;

import dev.mlml.Ilmarinen;
import dev.mlml.systems.Serialize;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents a Starboard configuration for a guild.
 */
@Data
public class StarboardGuild {
    private static final Logger logger = LoggerFactory.getLogger(StarboardGuild.class);

    @Serialize
    private int threshold = 3;
    @Serialize
    private String channelId;
    @Serialize
    private String emoji = "⭐";
    @Serialize
    private final String id;
    private Set<String> starredMessages = new HashSet<>();

    public StarboardGuild(String id) {
        this.id = id;
    }

    /**
     * Gets the starboard channel for this guild.
     *
     * @return the TextChannel for the starboard, or null if not set
     */
    public TextChannel getChannel() {
        if (channelId == null) {
            return null;
        }
        return Ilmarinen.getJda().getTextChannelById(channelId);
    }

    /**
     * Adds a message to the starred messages set.
     *
     * @param message the message to add
     */
    public void addStarredMessage(Message message) {
        starredMessages.add(message.getId());
    }

    /**
     * Checks if a message is already starred.
     *
     * @param message the message to check
     * @return true if the message is already starred, false otherwise
     */
    public boolean isAlreadyStarred(Message message) {
        return starredMessages.contains(message.getId());
    }

    /**
     * Removes a message from the starred messages set.
     *
     * @param messageId the ID of the message to remove
     */
    public void removeStarredMessage(String messageId) {
        starredMessages.remove(messageId);
    }

    /**
     * Sends a starboard message for the given message with the specified star count.
     * If the message is already starred, it updates the existing starboard message.
     *
     * @param message   the original message to be starred
     * @param reactionCounts a map of reaction emojis to their counts
     */
    public void sendStarboardMessage(Message message, Map<String, Integer> reactionCounts) {
        TextChannel starboardChannel = getChannel();
        if (starboardChannel == null) {
            logger.warn("Starboard channel not found for guild {}", id);
            return;
        }

        EmbedBuilder embed = createStarboardEmbed(message, reactionCounts);

        starboardChannel.sendMessageEmbeds(embed.build()).queue(starboardMessage -> {
                                                                    addStarredMessage(message);
                                                                    logger.debug("Sent starboard message for {} in guild {}", message.getId(), id);
                                                                }, throwable -> logger.error("Failed to send starboard message", throwable)
        );
    }

    /**
     * Updates an existing starboard message for the given original message with the new star count.
     * If the star count is below the threshold, it deletes the starboard message after a delay.
     *
     * @param originalMessage the original message that was starred
     * @param reactionCounts  a map of reaction emojis to their counts
     * @param starCount       the current star count for the original message, calculated from unique users if the emoji is "*"
     */
    public void updateStarboardMessage(Message originalMessage, Map<String, Integer> reactionCounts, int starCount) {
        TextChannel starboardChannel = getChannel();
        if (starboardChannel == null) {
            return;
        }

        starboardChannel.getHistory().retrievePast(100).queue(messages -> {
            for (Message starboardMessage : messages) {
                if (starboardMessage.getEmbeds().isEmpty()) {
                    continue;
                }

                MessageEmbed embed = starboardMessage.getEmbeds()
                        .get(0);
                if (embed.getAuthor() != null && embed.getAuthor().getUrl() != null && embed.getAuthor()
                                                                                            .getUrl()
                                                                                            .contains(originalMessage.getId())) {

                    EmbedBuilder updatedEmbed = createStarboardEmbed(originalMessage, reactionCounts);

                    if (starCount < threshold) {
                        starboardMessage.editMessage("⚠️ Deleting in 10 seconds!")
                                        .setEmbeds(updatedEmbed.build())
                                        .queue();

                        starboardMessage.delete()
                                        .queueAfter(10,
                                                    java.util.concurrent.TimeUnit.SECONDS,
                                                    success -> removeStarredMessage(originalMessage.getId()),
                                                    throwable -> logger.error("Failed to delete starboard message",
                                                                              throwable
                                                    )
                                        );
                    } else {
                        starboardMessage.editMessage("✨Updated!").setEmbeds(updatedEmbed.build()).queue();
                    }
                    break;
                }
            }
        });
    }


    private EmbedBuilder createStarboardEmbed(Message message, Map<String, Integer> reactionCounts) {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setAuthor(message.getAuthor().getAsTag(), message.getJumpUrl(), message.getAuthor().getAvatarUrl());

        embed.setColor(new Color(255, 215, 0));
        embed.setTimestamp(Instant.now());
        embed.setFooter(generateFooterText(reactionCounts));

        embed.setTitle("#" + message.getChannel().getName() + " (Jump!)", message.getJumpUrl());

        if (!message.getContentRaw().isEmpty()) {
            embed.setDescription(message.getContentRaw());
        }

        if (!message.getAttachments().isEmpty()) {
            Message.Attachment attachment = message.getAttachments()
                    .get(0);
            if (attachment.isImage()) {
                embed.setImage(attachment.getUrl());
            }
        }

        return embed;
    }

    private String generateFooterText(Map<String, Integer> reactionCounts) {
        StringBuilder footer = new StringBuilder();
        reactionCounts.forEach((emoji, count) -> {
            if (emoji.equals(this.emoji) || this.emoji.equals("*")) {
                footer.append(count).append(" ").append(emoji).append(" ");
            }
        });
        return footer.toString();
    }

    /**
     * Gets the current starboard configuration for this guild.
     *
     * @return the StarboardConfig containing channel ID, threshold, and emoji
     */
    public StarboardConfig getConfig() {
        return new StarboardConfig(channelId, threshold, emoji);
    }

    public record StarboardConfig(String channelId, int threshold, String emoji) {
    }
}