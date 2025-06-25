package dev.mlml.systems.giveaway;

import dev.mlml.Ilmarinen;
import dev.mlml.systems.IO;
import lombok.Getter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * GiveawaySystem is responsible for managing giveaways for the bot.
 * It handles starting, ending, and rerolling giveaways, as well as processing user interactions.
 */
public class GiveawaySystem {
    private static final Logger logger = LoggerFactory.getLogger(GiveawaySystem.class);
    @Getter
    private static final Map<String, GiveawayGuild> guilds = new HashMap<>();
    @Getter
    private static final Map<String, GiveawayData> allGiveaways = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * Retrieves or creates a GiveawayGuild instance for the specified guild ID.
     * @param id The ID of the guild.
     * @return The GiveawayGuild instance for the specified guild.
     */
    public static GiveawayGuild getGuild(String id) {
        return guilds.computeIfAbsent(id, GiveawayGuild::new);
    }

    /**
     * Starts a new giveaway in the specified guild and channel.
     * @param guildId The guild to start the giveaway in.
     * @param channelId The channel to post the giveaway in.
     * @param hostId The user who is hosting the giveaway.
     * @param prize The prize to display in the giveaway.
     * @param winners The number of winners for the giveaway.
     * @param endTime The time when the giveaway will end.
     * @param onSuccess A callback to execute on successful giveaway creation.
     * @param onError A callback to execute if an error occurs during giveaway creation.
     */
    public static void startGiveaway(String guildId, String channelId, String hostId, String prize, int winners, LocalDateTime endTime, Consumer<String> onSuccess, Consumer<String> onError) {
        try {
            Guild guild = Ilmarinen.getJda().getGuildById(guildId);
            if (guild == null) {
                onError.accept("Guild not found");
                return;
            }

            TextChannel channel = guild.getTextChannelById(channelId);
            if (channel == null) {
                onError.accept("Channel not found");
                return;
            }

            User host = Ilmarinen.getJda().retrieveUserById(hostId).complete();
            if (host == null) {
                onError.accept("Host not found");
                return;
            }

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("🎉 " + prize + " 🎉");
            embed.setDescription(String.format(
                    "**%d** winner%s!\n**Host:** %s\n**Ends:** <t:%d:R>\n\nClick the button below to enter!",
                    winners,
                    winners == 1 ? "" : "s",
                    host.getAsMention(),
                    endTime.toEpochSecond(java.time.ZoneOffset.UTC)
            ));
            embed.setColor(Color.GREEN);
            embed.setFooter("Giveaway ends");
            embed.setTimestamp(endTime.toInstant(java.time.ZoneOffset.UTC));

            Button enterButton = Button.success("giveaway_enter", "🎉 Enter Giveaway");

            channel.sendMessage("🎉 **GIVEAWAY** 🎉")
                   .setEmbeds(embed.build())
                   .setActionRow(enterButton)
                   .queue(message -> {
                              GiveawayData giveaway = new GiveawayData(message.getId());
                              giveaway.setGuildId(guildId);
                              giveaway.setChannelId(channelId);
                              giveaway.setHostId(hostId);
                              giveaway.setPrize(prize);
                              giveaway.setWinners(winners);
                              giveaway.setEndTime(endTime);

                              getGuild(guildId).addGiveaway(giveaway);
                              allGiveaways.put(message.getId(), giveaway);
                              scheduleGiveawayEnd(giveaway);
                              onSuccess.accept(message.getId());

                              logger.info("Started giveaway {} in guild {}", message.getId(), guildId);
                              IO.getSystem(GiveawayIO.class).save();
                          }, throwable -> {
                              logger.error("Failed to send giveaway message", throwable);
                              onError.accept("Failed to send giveaway message: " + throwable.getMessage());
                          }
                   );

        } catch (Exception e) {
            logger.error("Error starting giveaway", e);
            onError.accept("An error occurred: " + e.getMessage());
        }
    }

    /**
     * Ends a giveaway by its message ID.
     * @param messageId The ID of the giveaway message.
     * @param onSuccess A callback to execute on successful giveaway end.
     * @param onError A callback to execute if an error occurs during giveaway end.
     */
    public static void endGiveaway(String messageId, Runnable onSuccess, Consumer<String> onError) {
        GiveawayData giveaway = allGiveaways.get(messageId);

        if (giveaway == null) {
            onError.accept("Giveaway not found");
            return;
        }

        endGiveawayInternal(giveaway, onSuccess, onError);
    }

    /**
     * Rerolls a giveaway by its message ID.
     * @param messageId The ID of the giveaway message.
     * @param onSuccess A callback to execute on successful reroll.
     * @param onError A callback to execute if an error occurs during reroll.
     */
    public static void rerollGiveaway(String messageId, Runnable onSuccess, Consumer<String> onError) {
        GiveawayData giveaway = allGiveaways.get(messageId);

        if (giveaway == null) {
            onError.accept("Giveaway not found");
            return;
        }

        if (giveaway.isActive()) {
            onError.accept("Cannot reroll an active giveaway");
            return;
        }

        selectAndAnnounceWinners(giveaway, true);
        onSuccess.run();
    }

    /**
     * Rerolls a giveaway in a specific guild by its message ID.
     * @param guildId The ID of the guild where the giveaway is hosted.
     * @param messageId The ID of the giveaway message.
     * @param onSuccess A callback to execute on successful reroll.
     * @param onError A callback to execute if an error occurs during reroll.
     */
    public static void cancelGiveaway(String guildId, String messageId, Runnable onSuccess, Consumer<String> onError) {
        GiveawayData giveaway = allGiveaways.get(messageId);

        if (giveaway == null) {
            onError.accept("Giveaway not found");
            return;
        }

        try {
            Guild discordGuild = Ilmarinen.getJda().getGuildById(guildId);
            if (discordGuild != null) {
                TextChannel channel = discordGuild.getTextChannelById(giveaway.getChannelId());
                if (channel != null) {
                    channel.retrieveMessageById(messageId).queue(message -> {
                        EmbedBuilder embed = new EmbedBuilder();
                        embed.setTitle("🚫 Giveaway Cancelled");
                        embed.setDescription("This giveaway has been cancelled by the moderators.");
                        embed.setColor(Color.RED);

                        message.editMessageEmbeds(embed.build()).setComponents().queue();
                    });
                }
            }

            getGuild(guildId).removeGiveaway(messageId);
            allGiveaways.remove(messageId);
            IO.getSystem(GiveawayIO.class).save();
            onSuccess.run();

        } catch (Exception e) {
            logger.error("Error cancelling giveaway", e);
            onError.accept("An error occurred: " + e.getMessage());
        }
    }

    /**
     * Handles button interactions for giveaway entries.
     * @param event The ButtonInteractionEvent containing the interaction details.
     */
    public static void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().equals("giveaway_enter")) {
            return;
        }

        String messageId = event.getMessageId();
        String userId = event.getUser().getId();

        GiveawayData giveaway = allGiveaways.get(messageId);

        if (giveaway == null) {
            event.reply("This giveaway is no longer active.").setEphemeral(true).queue();
            return;
        }

        if (!giveaway.isActive()) {
            event.reply("This giveaway has already ended.").setEphemeral(true).queue();
            return;
        }

        if (giveaway.hasParticipant(userId)) {
            giveaway.removeParticipant(userId);
            event.reply("You have left the giveaway!").setEphemeral(true).queue();
        } else {
            giveaway.addParticipant(userId);
            event.reply("You have entered the giveaway! Good luck!").setEphemeral(true).queue();
        }

        IO.getSystem(GiveawayIO.class).save();
    }

    private static void scheduleGiveawayEnd(GiveawayData giveaway) {
        long delay = java.time.Duration.between(LocalDateTime.now(), giveaway.getEndTime()).toMillis();

        if (delay <= 0) {
            endGiveawayInternal(giveaway, () -> {
                                }, error -> logger.error("Error ending scheduled giveaway: {}", error)
            );
            return;
        }

        scheduler.schedule(() -> {
                               endGiveawayInternal(giveaway,
                                                   () -> logger.info("Automatically ended giveaway {}", giveaway.getMessageId()),
                                                   error -> logger.error("Error ending scheduled giveaway: {}", error)
                               );
                           }, delay, TimeUnit.MILLISECONDS
        );
    }

    private static void endGiveawayInternal(GiveawayData giveaway, Runnable onSuccess, Consumer<String> onError) {
        try {
            Guild guild = Ilmarinen.getJda().getGuildById(giveaway.getGuildId());
            if (guild == null) {
                onError.accept("Guild not found");
                return;
            }

            TextChannel channel = guild.getTextChannelById(giveaway.getChannelId());
            if (channel == null) {
                onError.accept("Channel not found");
                return;
            }

            channel.retrieveMessageById(giveaway.getMessageId()).queue(message -> {
                                                                           giveaway.setActive(false);
                                                                           selectAndAnnounceWinners(giveaway, false);

                                                                           EmbedBuilder embed = getEmbedBuilder(giveaway);

                                                                           message.editMessageEmbeds(embed.build()).setComponents().queue();

                                                                           getGuild(giveaway.getGuildId()).removeGiveaway(giveaway.getMessageId());
                                                                           scheduleGiveawayRemoval(giveaway.getMessageId());
                                                                           IO.getSystem(GiveawayIO.class).save();
                                                                           onSuccess.run();

                                                                       }, throwable -> {
                                                                           logger.error("Failed to retrieve giveaway message for ending", throwable);
                                                                           onError.accept("Failed to end giveaway: " + throwable.getMessage());
                                                                       }
            );

        } catch (Exception e) {
            logger.error("Error ending giveaway", e);
            onError.accept("An error occurred: " + e.getMessage());
        }
    }

    private static void scheduleGiveawayRemoval(String id, Duration delay) {
        scheduler.schedule(() -> {
            GiveawayData giveaway = allGiveaways.remove(id);
            if (giveaway != null) {
                getGuild(giveaway.getGuildId()).removeGiveaway(id);
                IO.getSystem(GiveawayIO.class).save();
                logger.info("Removed giveaway {} after delay", id);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static void scheduleGiveawayRemoval(String id) {
        scheduleGiveawayRemoval(id, Duration.ofMinutes(5));
    }

    @NotNull
    private static EmbedBuilder getEmbedBuilder(GiveawayData giveaway) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎉 " + giveaway.getPrize() + " 🎉");
        embed.setDescription("**This giveaway has ended!**\n\nWinners have been announced below.");
        embed.setColor(Color.RED);
        embed.setFooter("Giveaway ended");
        return embed;
    }

    private static void selectAndAnnounceWinners(GiveawayData giveaway, boolean isReroll) {
        try {
            Guild guild = Ilmarinen.getJda().getGuildById(giveaway.getGuildId());
            TextChannel channel = guild.getTextChannelById(giveaway.getChannelId());

            Set<String> participantSet = giveaway.getParticipantSet();

            if (participantSet.isEmpty()) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("😢 No Winners");
                embed.setDescription("Not enough participants to determine a winner for **" + giveaway.getPrize() + "**");
                embed.setColor(Color.ORANGE);

                channel.sendMessageEmbeds(embed.build()).queue();
                return;
            }

            List<String> participants = new ArrayList<>(participantSet);
            Collections.shuffle(participants);

            int winnersCount = Math.min(giveaway.getWinners(), participants.size());
            List<String> winners = participants.subList(0, winnersCount);

            StringBuilder winnerMentions = new StringBuilder();
            StringBuilder winnerList = new StringBuilder();

            Collection<CacheRestAction<User>> userActions = new ArrayList<>();
            winners.forEach(winner -> {
                userActions.add(guild.getJDA().retrieveUserById(winner));
            });

            RestAction.allOf(userActions).queue(users -> {
                for (int i = 0; i < winners.size(); i++) {
                    User winner = users.get(i);
                    if (winner != null) {
                        winnerMentions.append(winner.getAsMention()).append(" ");
                        winnerList.append((i + 1)).append(". ").append(winner.getEffectiveName()).append("\n");
                    }
                }

                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("🎉 " + (isReroll ? "Rerolled " : "") + "Winners!");
                embed.setDescription("**Prize:** " + giveaway.getPrize() + "\n\n**Winners:**\n" + winnerList);
                embed.setColor(Color.YELLOW);

                String message = "🎉 Congratulations " + winnerMentions + "! You won **" + giveaway.getPrize() + "**!";

                channel.sendMessage(message).setEmbeds(embed.build()).queue();
            });

        } catch (Exception e) {
            logger.error("Error selecting winners", e);
        }
    }

    /**
     * Initializes scheduled giveaways that are still active.
     * This method should be called on startup to ensure all active giveaways are scheduled.
     */
    public static void initializeScheduledGiveaways() {
        for (GiveawayData giveaway : allGiveaways.values()) {
            if (giveaway.isActive() && giveaway.getEndTime().isAfter(LocalDateTime.now())) {
                scheduleGiveawayEnd(giveaway);
            }
        }
    }
}

