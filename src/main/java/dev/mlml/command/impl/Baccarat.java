package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.argument.MoneyArgument;
import dev.mlml.command.argument.OptionArgument;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.systems.IO;
import dev.mlml.systems.economy.*;
import dev.mlml.util.CasinoDeck;
import dev.mlml.util.CasinoDeck.Card;
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

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@CommandInfo(
        keywords = {"baccarat", "bacc"},
        name = "Baccarat",
        description = "Play a game of Baccarat",
        category = CommandInfo.Category.Economy
)
public class Baccarat extends Command {
    private static final MoneyArgument BET_ARG = new MoneyArgument.Builder("bet").description("The amount to bet")
            .require()
            .get();
    private static final OptionArgument CHOICE_ARG = new OptionArgument.Builder("choice").description(
                    "Bet on Player, Banker, or Tie")
            .require().addOptions(List.of("player", "banker", "tie"))
            .get();

    private static final Map<String, BaccaratGame> games = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    public Baccarat() {
        super(BET_ARG, CHOICE_ARG);
    }

    public void execute(Context ctx) {
        float bet = ctx.getArgument(BET_ARG).map(ParsedArgument::value).orElse(0f);
        String choice = ctx.getArgument(CHOICE_ARG).map(ParsedArgument::value).orElse("player").toLowerCase();
        String channelId = ctx.getChannel().getId();

        if (bet <= 0) {
            ctx.fail("Bet amount must be positive!");
            return;
        }

        EconUser eu = EconomySystem.getUser(ctx.getMember().getId());
        EconGuild eg = EconomySystem.getGuild(ctx.getGuild().getId());
        GamblingInstance gi = new GamblingInstance(eu, eg);

        if (bet >= Float.MAX_VALUE) {
            bet = eu.getMoney();
        }

        if (!eu.canAfford(bet)) {
            ctx.fail("You don't have enough money!");
            return;
        }

        if (games.containsKey(channelId)) {
            BaccaratGame game = games.get(channelId);
            String result = game.joinPlayer(gi, bet, ctx.getMember(), choice);
            if (result != null) {
                ctx.fail(result);
            } else {
                ctx.getMessage().addReaction(Emoji.fromUnicode("\u2795")).queue();
            }
            return;
        }

        BaccaratGame game = games.computeIfAbsent(channelId, k -> new BaccaratGame((TextChannel) ctx.getChannel()));

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

        float finalBet = bet;
        future.thenRun(() -> {
            String result = game.joinPlayer(gi, finalBet, ctx.getMember(), choice);
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

    public static void handleBaccaratButton(ButtonInteractionEvent event) {
        BaccaratGame game = games.get(event.getChannelId());
        if (game == null) {
            event.reply("No active baccarat game in this channel!").setEphemeral(true).queue();
            return;
        }

        String action = event.getComponentId().split("_")[1];

        switch (action) {
            case "deal" -> game.startGame(event);
            case "rejoin" -> game.playerRejoin(event.getUser().getId(), event);
            default -> event.reply("Unknown action: " + action).setEphemeral(true).queue();
        }
    }

    private static class BaccaratGame {
        private static final long BETTING_TIME_SECONDS = 15;

        private final TextChannel channel;
        private final List<BaccaratPlayer> players = new ArrayList<>();
        private final CasinoDeck deck = new CasinoDeck(8);
        private final List<Card> playerHand = new ArrayList<>();
        private final List<Card> bankerHand = new ArrayList<>();
        private GameState state = GameState.WAITING;
        private Message currentGameMessage;
        private ScheduledFuture<?> bettingTimer;
        @Setter
        private Runnable messageReadyCallback;

        private final BaccaratGame previousGame;

        public BaccaratGame(TextChannel channel) {
            this(channel, null);
        }

        public BaccaratGame(TextChannel channel, BaccaratGame previousGame) {
            this.channel = channel;
            this.previousGame = previousGame;
            startBettingTimer();
            updateGameMessage();
        }

        private void startBettingTimer() {
            bettingTimer = executor.schedule(() -> {
                                                 if (!players.isEmpty()) {
                                                     startGame(null);
                                                 } else {
                                                     currentGameMessage.editMessage("Game cancelled due to no players joining.").queue();
                                                     games.remove(channel.getId());
                                                 }
                                             }, BETTING_TIME_SECONDS, TimeUnit.SECONDS
            );
        }

        public String joinPlayer(GamblingInstance gi, float bet, Member playerMember, String choice) {
            if (state != GameState.WAITING) {
                return "Game has already started!";
            }

            if (players.stream().anyMatch(p -> p.getMember().getId().equals(playerMember.getId()))) {
                return "You are already in the game!";
            }

            gi.play(bet);
            players.add(new BaccaratPlayer(gi, bet, playerMember, choice));
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

            BaccaratPlayer previousPlayer = previousGame.players.stream()
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

            joinPlayer(previousPlayer.getGi(),
                       previousPlayer.getBet(),
                       previousPlayer.getMember(),
                       previousPlayer.getChoice()
            );
            updateGameMessage();

            if (event != null) {
                event.deferEdit().queue();
            }
        }

        public void startGame(ButtonInteractionEvent event) {
            if (state != GameState.WAITING) {
                return;
            }
            if (bettingTimer != null) {
                bettingTimer.cancel(false);
            }

            state = GameState.IN_PROGRESS;

            playerHand.add(deck.drawCard());
            bankerHand.add(deck.drawCard());
            playerHand.add(deck.drawCard());
            bankerHand.add(deck.drawCard());

            int playerValue = getHandValue(playerHand);
            int bankerValue = getHandValue(bankerHand);

            if (playerValue < 8 && bankerValue < 8) {
                if (playerValue <= 5) {
                    playerHand.add(deck.drawCard());
                }
                if (playerHand.size() == 2) {
                    if (bankerValue <= 5) {
                        bankerHand.add(deck.drawCard());
                    }
                } else {
                    Card playerThirdCard = playerHand.get(2);
                    if (bankerValue <= 2) {
                        bankerHand.add(deck.drawCard());
                    } else if (bankerValue == 3 && playerThirdCard.rank().getValue() != 8) {
                        bankerHand.add(deck.drawCard());
                    } else if (bankerValue == 4 && playerThirdCard.rank().getValue() >= 2 && playerThirdCard.rank()
                                                                                                            .getValue() <= 7) {
                        bankerHand.add(deck.drawCard());
                    } else if (bankerValue == 5 && playerThirdCard.rank().getValue() >= 4 && playerThirdCard.rank()
                                                                                                            .getValue() <= 7) {
                        bankerHand.add(deck.drawCard());
                    } else if (bankerValue == 6 && (playerThirdCard.rank().getValue() == 6 || playerThirdCard.rank()
                                                                                                             .getValue() == 7)) {
                        bankerHand.add(deck.drawCard());
                    }
                }
            }
            endGame();
        }

        private void endGame() {
            state = GameState.COMPLETED;
            int playerValue = getHandValue(playerHand);
            int bankerValue = getHandValue(bankerHand);

            String result;
            if (playerValue > bankerValue) {
                result = "Player";
            } else if (bankerValue > playerValue) {
                result = "Banker";
            } else {
                result = "Tie";
            }

            for (BaccaratPlayer player : players) {
                if (player.getChoice().equalsIgnoreCase(result)) {
                    float payout = 1.0f;
                    if (result.equals("Banker")) {
                        payout = 0.95f;
                    } else if (result.equals("Tie")) {
                        payout = 8.0f;
                    }
                    player.win(player.getBet() * (1 + payout));
                    player.setResult("WIN");
                } else {
                    player.setResult("LOSE");
                }
            }
            IO.getSystem(EconIO.class).save();
            updateGameMessage();

            if (bettingTimer != null) {
                bettingTimer.cancel(false);
            }

            games.remove(channel.getId());
            games.put(channel.getId(), new BaccaratGame(channel, this));
        }

        private int getHandValue(List<Card> hand) {
            int value = 0;
            for (Card card : hand) {
                if (card.rank().getValue() >= 10) {
                    value += 0;
                } else {
                    value += card.rank().getValue();
                }
            }
            return value % 10;
        }

        private void updateGameMessage() {
            EmbedBuilder eb = new EmbedBuilder().setTitle("🎲 Baccarat 🎲").setColor(0xC71585);

            if (state == GameState.WAITING) {
                eb.setDescription(String.format(
                        "Place your bets! Use `!baccarat <bet> <player|banker|tie>`.\nDealing in %d seconds...",
                        BETTING_TIME_SECONDS
                ));
                eb.addField("Players",
                            players.stream()
                                   .map(p -> p.getMember()
                                              .getEffectiveName() + " ($" + p.getBet() + " on " + p.getChoice() + ")")
                                   .collect(Collectors.joining("\n")),
                            false
                );
            } else {
                eb.addField("Player Hand",
                            formatCards(playerHand) + " (Value: " + getHandValue(playerHand) + ")",
                            true
                );
                eb.addField("Banker Hand",
                            formatCards(bankerHand) + " (Value: " + getHandValue(bankerHand) + ")",
                            true
                );

                StringBuilder results = new StringBuilder();
                for (BaccaratPlayer player : players) {
                    results.append(player.getMember().getEffectiveName())
                           .append(": ")
                           .append(player.getResult())
                           .append("\n");
                }
                eb.addField("Results", results.toString(), false);
            }

            List<Button> buttons = new ArrayList<>();
            if (state == GameState.WAITING) {
                buttons.add(Button.primary("baccarat_deal", "Deal Now"));
                if (Objects.nonNull(previousGame)) {
                    buttons.add(Button.secondary("baccarat_rejoin", "Join with previous bet")
                                      .withEmoji(Emoji.fromUnicode("🔙")));
                }
            }

            if (currentGameMessage == null) {
                MessageCreateBuilder mb = new MessageCreateBuilder().setEmbeds(eb.build()).addActionRow(buttons);
                channel.sendMessage(mb.build()).queue(message -> {
                    currentGameMessage = message;
                    if (messageReadyCallback != null) {
                        messageReadyCallback.run();
                        messageReadyCallback = null;
                    }
                });
            } else {
                MessageEditBuilder meb = new MessageEditBuilder().setEmbeds(eb.build());
                if (!buttons.isEmpty()) {
                    meb.setActionRow(buttons);
                } else {
                    meb.setComponents(Collections.emptyList());
                }
                currentGameMessage.editMessage(meb.build()).queue();
            }
        }

        private String formatCards(List<Card> hand) {
            return hand.stream().map(Card::toString).collect(Collectors.joining(" "));
        }

        private enum GameState {WAITING, IN_PROGRESS, COMPLETED}
    }

    @Data
    private static class BaccaratPlayer {
        private final GamblingInstance gi;
        private final float bet;
        private final Member member;
        private final String id;
        private final String choice; // Player, Banker, or Tie
        private String result;

        public BaccaratPlayer(GamblingInstance gi, float bet, Member member, String choice) {
            this.gi = gi;
            this.bet = bet;
            this.member = member;
            this.id = member.getId();
            this.choice = choice.toLowerCase();
        }

        public void win(float amount) {
            gi.win(amount);
        }
    }
}
