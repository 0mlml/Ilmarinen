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
import java.util.Set;

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
    @Serialize
    private Set<String> starredMessages = new HashSet<>();

    public StarboardGuild(String id) {
        this.id = id;
    }

    public TextChannel getChannel() {
        if (channelId == null) {
            return null;
        }
        return Ilmarinen.getJda().getTextChannelById(channelId);
    }

    public void addStarredMessage(Message message) {
        starredMessages.add(message.getId());
    }

    public boolean isAlreadyStarred(Message message) {
        return starredMessages.contains(message.getId());
    }

    public void removeStarredMessage(String messageId) {
        starredMessages.remove(messageId);
    }

    public void sendStarboardMessage(Message message, int starCount) {
        TextChannel starboardChannel = getChannel();
        if (starboardChannel == null) {
            logger.warn("Starboard channel not found for guild {}", id);
            return;
        }

        if (isAlreadyStarred(message)) {
            updateStarboardMessage(message, starCount);
            return;
        }

        EmbedBuilder embed = createStarboardEmbed(message, starCount);

        starboardChannel.sendMessageEmbeds(embed.build())
                        .queue(
                                starboardMessage -> {
                                    addStarredMessage(message);
                                    logger.debug("Sent starboard message for {} in guild {}", message.getId(), id);
                                },
                                throwable -> logger.error("Failed to send starboard message", throwable)
                        );
    }

    public void updateStarboardMessage(Message originalMessage, int starCount) {
        TextChannel starboardChannel = getChannel();
        if (starboardChannel == null) {
            return;
        }

        starboardChannel.getHistory().retrievePast(100).queue(messages -> {
            for (Message starboardMessage : messages) {
                if (starboardMessage.getEmbeds().isEmpty()) continue;

                MessageEmbed embed = starboardMessage.getEmbeds().get(0);
                if (embed.getAuthor() != null &&
                        embed.getAuthor().getUrl() != null &&
                        embed.getAuthor().getUrl().contains(originalMessage.getId())) {

                    EmbedBuilder updatedEmbed = createStarboardEmbed(originalMessage, starCount);

                    if (starCount < threshold) {
                        starboardMessage.editMessage("⚠️ Deleting in 10 seconds!")
                                        .setEmbeds(updatedEmbed.build())
                                        .queue();

                        starboardMessage.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS,
                                                             success -> removeStarredMessage(originalMessage.getId()),
                                                             throwable -> logger.error("Failed to delete starboard message", throwable)
                        );
                    } else {
                        starboardMessage.editMessage("✨ Updated!")
                                        .setEmbeds(updatedEmbed.build())
                                        .queue();
                    }
                    break;
                }
            }
        });
    }

    private EmbedBuilder createStarboardEmbed(Message message, int starCount) {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setAuthor(
                message.getAuthor().getAsTag(),
                message.getJumpUrl(),
                message.getAuthor().getAvatarUrl()
        );

        embed.setColor(new Color(255, 215, 0));
        embed.setTimestamp(Instant.now());
        embed.setFooter(starCount + emoji);

        embed.setTitle("#" + message.getChannel().getName() + " (Jump!)", message.getJumpUrl());

        if (!message.getContentRaw().isEmpty()) {
            embed.setDescription(message.getContentRaw());
        }

        if (!message.getAttachments().isEmpty()) {
            Message.Attachment attachment = message.getAttachments().get(0);
            if (attachment.isImage()) {
                embed.setImage(attachment.getUrl());
            }
        }

        return embed;
    }

    public StarboardConfig getConfig() {
        return new StarboardConfig(channelId, threshold, emoji);
    }

    public record StarboardConfig(String channelId, int threshold, String emoji) {
    }
}