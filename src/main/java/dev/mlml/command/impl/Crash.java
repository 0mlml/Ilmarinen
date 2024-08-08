package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.argument.MoneyArgument;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.economy.*;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@CommandInfo(
        keywords = {"crash"},
        name = "CrashGame",
        description = "Crash gambling (economy)",
        category = CommandInfo.Category.Economy
)
public class Crash extends Command {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(Crash.class);

    private static final MoneyArgument AMOUNT_ARG = new MoneyArgument.Builder("amount")
            .description("The amount of money to bet")
            .require()
            .get();

    private static final Map<String, CrashGame> games = new ConcurrentHashMap<>();

    public Crash() {
        super(AMOUNT_ARG);
    }

    @Override
    public void execute(Context ctx) {
        float amount = ctx.getArgument(AMOUNT_ARG).map(ParsedArgument::getValue).orElse(0f);
        String channelId = ctx.getChannel().getId();

        if (amount <= 0) {
            ctx.fail("Invalid amount!");
            return;
        }

        EconUser eu = Economy.getUser(ctx.getMember().getId());

        if (amount >= Float.MAX_VALUE) {
            amount = eu.getMoney();
        }

        if (!eu.canAfford(amount)) {
            ctx.fail("You do not have enough money!");
            return;
        }

        EconGuild eg = Economy.getGuild(ctx.getGuild().getId());
        GamblingInstance gi = new GamblingInstance(eu, eg);

        CrashGame existingCrash = games.get(channelId);
        if (existingCrash != null && existingCrash.notJoinable()) {
            ctx.fail("A game is already in progress!");
            return;
        }

        CrashGame game = games.computeIfAbsent(channelId, k -> new CrashGame((TextChannel) ctx.getChannel()));
        String failReason = game.joinPlayer(gi, amount);

        if (failReason != null) {
            ctx.fail(failReason);
        }

        IO.save();
    }

    public static void handleCrashLeaveButton(ButtonInteractionEvent event) {
        CrashGame game = games.get(event.getChannelId());
        if (game == null) {
            event.reply("This button does not correspond to an active game!").setEphemeral(true).queue();
        } else {
            game.cashOut(event.getUser().getId(), event);
        }
    }

    @Data
    private static class CrashGame {
        private static final int TICK_RATE = 100;
        private static final float BASE_INCREMENT = 1f / TICK_RATE;
        private static final int TICK_MILLIS = 1000 / TICK_RATE;

        private final TextChannel channel;
        private final List<Player> players;
        private final MessageEmbed embed;
        private final float maxMultiplier;
        private final long startTime;
        private float currentMultiplier;
        private boolean crashed;
        private long lastUpdate;
        private Timer ticker;

        public CrashGame(TextChannel channel) {
            this.channel = channel;
            this.players = new ArrayList<>();
            this.embed = new EmbedBuilder().setTitle("Crash!")
                                           .setColor(0x7771d1)
                                           .setAuthor("Gambling")
                                           .setDescription(
                                                   "Join using the `ecrash <amt>` command!\nStarting in 5 seconds!")
                                           .setFooter("I love gambling!")
                                           .build();
            this.maxMultiplier = genMultiplier();
            this.currentMultiplier = 1;
            this.crashed = false;
            this.startTime = System.currentTimeMillis() + 5000;
            this.lastUpdate = System.currentTimeMillis();

            this.channel.sendMessageEmbeds(embed).queue(message -> {
                this.ticker = new Timer();
                this.ticker.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        tick(message);
                    }
                }, 0, TICK_MILLIS);
            });
        }

        private float genMultiplier() {
            double p = 0.01;
            double multiplier = Math.floor(Math.log(1 - Math.random()) / Math.log(1 - p)) / 10 + 1;
            return (float) multiplier;
        }

        private void tick(Message message) {
            long now = System.currentTimeMillis();
            if (now < startTime) {
                return;
            }

            currentMultiplier += BASE_INCREMENT;
            if (currentMultiplier > maxMultiplier) {
                currentMultiplier = maxMultiplier;
                crashed = true;
            }

            if (players.stream().allMatch(Player::isOut)) {
                crashed = true;
            }

            if (now - lastUpdate >= 3000) {
                EmbedBuilder embedBuilder = new EmbedBuilder(embed).setDescription("Current multiplier: x" + String.format(
                        "%.3f",
                        currentMultiplier
                )).setTitle("🟢 Crash");
                message.editMessageEmbeds(embedBuilder.build())
                       .setActionRow(Button.success("crash_leave", "Cash out").withEmoji(Emoji.fromUnicode("🤑")))
                       .queue();
                lastUpdate = now;
            } else if (crashed) {
                EmbedBuilder resultsEmbed = new EmbedBuilder().setTitle("Crash results")
                                                              .setColor(0x7771d1)
                                                              .setDescription(getResultsDescription());

                EmbedBuilder crashedEmbed = new EmbedBuilder(embed).setDescription("Crashed!\nMultiplier: x" + maxMultiplier)
                                                                   .setTitle("🔴 Crash");


                message.editMessageEmbeds(crashedEmbed.build(), resultsEmbed.build())
                       .and(message.editMessageComponents())
                       .queue();

                IO.save();
                ticker.cancel();
                games.remove(channel.getId());
            }
        }

        private String getResultsDescription() {
            players.sort(Comparator.comparing(Player::getOutMultiplier).reversed());
            StringBuilder description = new StringBuilder();
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                if (player.isOut()) {
                    description.append(String.format("**%d.** <@%s> : $%.2f @ x%.2f\n",
                                                     i + 1,
                                                     player.getId(),
                                                     player.getWinnings(),
                                                     player.getOutMultiplier()
                    ));
                } else {
                    description.append(String.format("<@%s> : -$%.2f\n", player.getId(), player.getAmount()));
                }
            }
            return description.toString();
        }

        public String joinPlayer(GamblingInstance gi, float amount) {
            if (notJoinable()) {
                return "Game has already started!";
            }

            if (players.stream().anyMatch(p -> p.getId().equals(gi.getUser().getId()))) {
                return "You have already joined!";
            }

            players.add(new Player(gi, amount));

            return null;
        }

        public boolean notJoinable() {
            return System.currentTimeMillis() >= startTime;
        }

        public void cashOut(String userId, ButtonInteractionEvent event) {
            Player player = players.stream().filter(p -> p.getId().equals(userId)).findFirst().orElse(null);
            if (player == null) {
                event.reply("You never joined!").setEphemeral(true).queue();
                return;
            }
            if (player.isOut()) {
                event.reply("You have already cashed out!").setEphemeral(true).queue();
                return;
            }

            player.cashOut(currentMultiplier);

            event.reply(String.format("You won $%.2f at x%.2f", player.getWinnings(), player.getOutMultiplier()))
                 .setEphemeral(true)
                 .queue();
        }
    }

    @Data
    private static class Player {
        private final String id;
        private final float amount;
        private boolean out;
        private float outMultiplier;
        private float winnings;

        private GamblingInstance gi;

        public Player(GamblingInstance gi, float amount) {
            this.id = gi.getUser().getId();
            this.gi = gi;
            this.amount = amount;
            this.out = false;

            gi.play(amount);
        }

        public void cashOut(float multiplier) {
            this.out = true;
            this.outMultiplier = multiplier;
            this.winnings = amount * multiplier;

            gi.win(winnings);
        }
    }
}
