package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.argument.MoneyArgument;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.systems.IO;
import dev.mlml.systems.economy.*;
import lombok.Data;
import lombok.Setter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;

@CommandInfo(
        keywords = {"crash"},
        name = "CrashGame",
        description = "Crash gambling (economy)",
        category = CommandInfo.Category.Economy
)
public class Crash extends Command {
    private static final MoneyArgument AMOUNT_ARG = new MoneyArgument.Builder("amount").description(
                    "The amount of money to bet")
            .require()
            .get();

    private static final Map<String, CrashGame> games = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private static final long WAITING_TIME_SECONDS = 20;

    public Crash() {
        super(AMOUNT_ARG);
    }

    @Override
    public void execute(Context ctx) {
        float amount = ctx.getArgument(AMOUNT_ARG).map(ParsedArgument::value).orElse(0f);
        String channelId = ctx.getChannel().getId();

        if (amount <= 0) {
            ctx.fail("Invalid amount!");
            return;
        }

        EconUser eu = EconomySystem.getUser(ctx.getMember().getId());
        EconGuild eg = EconomySystem.getGuild(ctx.getGuild().getId());
        GamblingInstance gi = new GamblingInstance(eu, eg);

        if (amount >= Float.MAX_VALUE) {
            amount = eu.getMoney();
        }

        if (!eu.canAfford(amount)) {
            ctx.fail("You do not have enough money!");
            return;
        }

        if (games.containsKey(channelId)) {
            CrashGame game = games.get(channelId);
            String result = game.joinPlayer(gi, amount, ctx.getMember());
            if (result != null) {
                ctx.fail(result);
            } else {
                ctx.getMessage().addReaction(Emoji.fromUnicode("\u2705")).queue();
            }
            return;
        }

        CrashGame game = games.computeIfAbsent(channelId, k -> new CrashGame((TextChannel) ctx.getChannel()));
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (game.currentGameMessage != null) {
            future.complete(null);
        } else {
            game.setMessageReadyCallback(() -> future.complete(null));
            executor.schedule(() -> {
                                  if (!future.isDone()) {
                                      future.completeExceptionally(new TimeoutException("Timed out waiting for game message"));
                                  }
                              }, 5, TimeUnit.SECONDS
            );
        }

        float finalAmount = amount;
        future.thenRun(() -> {
            String result = game.joinPlayer(gi, finalAmount, ctx.getMember());
            if (result != null) {
                ctx.fail(result);
            } else {
                ctx.getMessage().addReaction(Emoji.fromUnicode("\u2705")).queue();
            }
        }).exceptionally(e -> {
            ctx.fail("An error occurred while waiting for the game to start: " + e.getMessage());
            return null;
        });
    }

    public static void handleCrashButton(ButtonInteractionEvent event) {
        CrashGame game = games.get(event.getChannelId());
        if (game == null) {
            event.reply("No active Crash game in this channel!").setEphemeral(true).queue();
            return;
        }

        String[] parts = event.getComponentId().split("_");
        String action = parts[1];
        String playerId = event.getUser().getId();

        switch (action) {
            case "leave" -> game.playerCashOut(playerId, event);
            case "start" -> game.startGame(event);
            case "rejoin" -> game.playerRejoin(event.getUser().getId(), event);
            default -> event.reply("Invalid action").setEphemeral(true).queue();
        }
    }

    @Data
    private static class CrashGame {
        private static final int TICK_RATE = 100;
        private static final float BASE_INCREMENT = 1f / TICK_RATE * 0.1f;
        private static final int TICK_MILLIS = 1000 / TICK_RATE;

        private final TextChannel channel;
        private final List<CrashPlayer> players = new ArrayList<>();
        private Message currentGameMessage;
        private float currentMultiplier = 1f;
        private float currentIncrement = BASE_INCREMENT;
        private float maxMultiplier;
        private boolean crashed = false;
        private GameState state = GameState.WAITING;
        private ScheduledFuture<?> gameTimer;
        private long lastMessageUpdate = 0;
        private ScheduledFuture<?> ticker;
        @Setter
        private Runnable messageReadyCallback;
        private final CrashGame previousGame;

        public CrashGame(TextChannel channel) {
            this(channel, null);
        }

        public CrashGame(TextChannel channel, CrashGame previousGame) {
            this.channel = channel;
            this.previousGame = previousGame;
            this.maxMultiplier = genMultiplier();
            startWaitingTimer();
            updateGameMessage();
        }

        private enum GameState {
            WAITING, IN_PROGRESS, COMPLETED
        }

        private float genMultiplier() {
            return Math.max(1.0f, (float) ((1f - 0.02f) / (1f - Math.random())));
        }

        private void startWaitingTimer() {
            gameTimer = executor.schedule(() -> {
                                              if (!players.isEmpty()) {
                                                  startGame(null);
                                              } else {
                                                  currentGameMessage.editMessage("Game cancelled due to no players joining.").queue();
                                                  games.remove(channel.getId());
                                              }
                                          }, WAITING_TIME_SECONDS, TimeUnit.SECONDS
            );
        }

        public String joinPlayer(GamblingInstance gi, float amount, Member member) {
            if (state != GameState.WAITING) {
                return "Game has already started!";
            }

            if (players.stream().anyMatch(p -> p.getId().equals(member.getId()))) {
                return "You're already in the game!";
            }

            gi.play(amount);
            players.add(new CrashPlayer(gi, amount, member));
            updateGameMessage();
            return null;
        }

        public void playerRejoin(String playerId, ButtonInteractionEvent event) {
            if (state != GameState.WAITING) {
                if (event != null) {
                    event.reply("Game is already in progress!").setEphemeral(true).queue();
                }
                return;
            }

            if (players.stream().anyMatch(p -> p.getId().equals(playerId))) {
                if (event != null) {
                    event.reply("You are already in the game!").setEphemeral(true).queue();
                }
                return;
            }

            if (Objects.isNull(previousGame)) {
                if (event != null) {
                    event.reply("No previous game to rejoin!").setEphemeral(true).queue();
                }
                return;
            }

            CrashPlayer previousPlayer = previousGame.players.stream()
                                                             .filter(p -> p.getId().equals(playerId))
                                                             .findFirst()
                                                             .orElse(null);

            if (previousPlayer == null) {
                if (event != null) {
                    event.reply("You weren't in the previous game!").setEphemeral(true).queue();
                }
                return;
            }

            if (!previousPlayer.getGi().user().canAfford(previousPlayer.getBet())) {
                if (event != null) {
                    event.reply("You can't afford to rejoin with your previous bet!").setEphemeral(true).queue();
                }
                return;
            }

            joinPlayer(previousPlayer.getGi(), previousPlayer.getBet(), previousPlayer.getMember());
            updateGameMessage();

            if (event != null) {
                event.deferEdit().queue();
            }
        }

        public void startGame(ButtonInteractionEvent event) {
            if (state != GameState.WAITING) {
                return;
            }

            if (players.isEmpty()) {
                if (event != null) {
                    event.reply("No players have joined!").setEphemeral(true).queue();
                }
                return;
            }

            state = GameState.IN_PROGRESS;
            startTicker();
            updateGameMessage();

            if (event != null) {
                event.deferEdit().queue();
            }
        }

        private void startTicker() {
            ticker = executor.scheduleAtFixedRate(() -> {
                                                      if (state != GameState.IN_PROGRESS) {
                                                          return;
                                                      }

                                                      currentMultiplier += currentIncrement;
                                                      currentIncrement *= 1.001f;
                                                      if (currentMultiplier > maxMultiplier) {
                                                          currentMultiplier = maxMultiplier;
                                                          crashed = true;
                                                      }

                                                      if (players.stream().allMatch(CrashPlayer::hasCashedOut)) {
                                                          crashed = true;
                                                      }

                                                      if (crashed) {
                                                          endGame();
                                                      }

                                                      if (currentGameMessage != null && lastMessageUpdate + 3000 < System.currentTimeMillis()) {
                                                          updateGameMessage();
                                                          lastMessageUpdate = System.currentTimeMillis();
                                                      }
                                                  }, 0, TICK_MILLIS, TimeUnit.MILLISECONDS
            );
        }

        public void playerCashOut(String playerId, ButtonInteractionEvent event) {
            if (state != GameState.IN_PROGRESS) {
                if (event != null) {
                    event.reply("Game is not in progress!").setEphemeral(true).queue();
                }
                return;
            }

            CrashPlayer player = players.stream()
                                        .filter(p -> p.getId().equals(playerId) && !p.hasCashedOut())
                                        .findFirst()
                                        .orElse(null);

            if (player == null) {
                if (event != null) {
                    event.reply("You're not in the game or already cashed out!").setEphemeral(true).queue();
                }
                return;
            }

            player.cashOut(currentMultiplier);
            updateGameMessage();

            if (event != null) {
                event.reply(String.format("Cashed out at x%.2f!", currentMultiplier)).setEphemeral(true).queue();
            }

            if (players.stream().allMatch(CrashPlayer::hasCashedOut)) {
                crashed = true;
            }
        }

        private void endGame() {
            state = GameState.COMPLETED;

            updateGameMessage();

            if (ticker != null) {
                ticker.cancel(false);
            }
            if (gameTimer != null) {
                gameTimer.cancel(false);
            }

            IO.getSystem(EconIO.class).save();

            games.remove(channel.getId());
            games.put(channel.getId(), new CrashGame(channel, this));
        }

        private void updateGameMessage() {
            EmbedBuilder eb = getEmbedBuilder();

            StringBuilder results = new StringBuilder();
            players.stream().sorted(Comparator.comparing(CrashPlayer::getOutMultiplier).reversed()).forEach(player -> {
                switch (state) {
                    case WAITING -> results.append(String.format("%s: $%.2f\n",
                                                                 player.getMember().getEffectiveName(),
                                                                 player.getBet()
                    ));
                    case IN_PROGRESS -> results.append(String.format("%s: $%.2f%s\n",
                                                                     player.getMember().getEffectiveName(),
                                                                     player.getBet(),
                                                                     player.hasCashedOut()
                                                                     ? " (Cashed out at x" + String.format("%.2f",
                                                                                                           player.getOutMultiplier()
                                                                     ) + ")"
                                                                     : ""
                    ));
                    case COMPLETED -> results.append(String.format("%s: %s (Bet: $%.2f, Winnings: $%.2f)\n",
                                                                   player.getMember().getEffectiveName(),
                                                                   player.hasCashedOut()
                                                                   ? "Cashed out at x" + String.format("%.2f",
                                                                                                       player.getOutMultiplier()
                                                                   )
                                                                   : "Crashed!",
                                                                   player.getBet(),
                                                                   player.getWinnings()
                    ));
                }
            });
            eb.addField("Players", results.toString(), false);

            List<Button> buttons = new ArrayList<>();
            if (state == GameState.WAITING) {
                buttons.add(Button.primary("crash_start", "Start Game"));
                if (Objects.nonNull(previousGame)) {
                    buttons.add(Button.secondary("crash_rejoin", "Join with previous bet")
                                      .withEmoji(Emoji.fromUnicode("🔙")));
                }
            } else if (state == GameState.IN_PROGRESS && !crashed) {
                buttons.add(Button.success("crash_leave", "Cash Out").withEmoji(Emoji.fromUnicode("💰")));
            }

            if (currentGameMessage == null) {
                MessageCreateBuilder mb = new MessageCreateBuilder().setEmbeds(eb.build()).setActionRow(buttons);
                channel.sendMessage(mb.build()).queue(message -> {
                    currentGameMessage = message;
                    if (messageReadyCallback != null) {
                        messageReadyCallback.run();
                        messageReadyCallback = null;
                    }
                });
            } else {
                MessageEditBuilder ebuilder = new MessageEditBuilder().setEmbeds(eb.build())
                                                                      .setComponents(buttons.isEmpty()
                                                                                     ? Collections.emptyList()
                                                                                     : List.of(net.dv8tion.jda.api.interactions.components.ActionRow.of(
                                                                                             buttons)));
                currentGameMessage.editMessage(ebuilder.build()).queue(message -> currentGameMessage = message);
            }
        }

        @NotNull
        private EmbedBuilder getEmbedBuilder() {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("CRASH GAME");
            eb.setColor(state == GameState.IN_PROGRESS ? (crashed ? 0xFF0000 : 0x00FF00) : 0x7771d1);

            String description;
            if (state == GameState.WAITING) {
                description = "Game starting in " + WAITING_TIME_SECONDS + " seconds!\nUse `!crash <amount>` to join";
                if (previousGame != null) {
                    description += "\nMax multiplier last game: x" + String.format("%.2f", previousGame.maxMultiplier);
                }
            } else if (state == GameState.IN_PROGRESS) {
                description = "Current multiplier: x" + String.format("%.2f", currentMultiplier);
                if (crashed) {
                    description += "\n\n💥 CRASHED! 💥";
                }
            } else {
                description = "Game finished!\nFinal multiplier: x" + String.format("%.2f", maxMultiplier);
            }
            eb.setDescription(description);
            return eb;
        }
    }

    @Data
    private static class CrashPlayer {
        private final GamblingInstance gi;
        private final float bet;
        private final Member member;
        private final String id;
        private boolean cashedOut = false;
        private float outMultiplier = 0;
        private float winnings = 0;

        public CrashPlayer(GamblingInstance gi, float amount, Member member) {
            this.gi = gi;
            this.bet = amount;
            this.member = member;
            this.id = member.getId();
        }

        public void cashOut(float multiplier) {
            this.outMultiplier = multiplier;
            this.winnings = bet * multiplier;
            this.cashedOut = true;
            gi.win(winnings);
        }

        public boolean hasCashedOut() {
            return cashedOut;
        }
    }
}