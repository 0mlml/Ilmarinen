package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.argument.MoneyArgument;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.systems.IO;
import dev.mlml.systems.economy.*;
import dev.mlml.util.CasinoDeck.Card;
import dev.mlml.util.CasinoDeck.Rank;
import dev.mlml.util.Suit;
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
        keywords = {"blackjack", "bj"},
        name = "Blackjack",
        description = "Play a game of Blackjack",
        category = CommandInfo.Category.Economy
)
public class Blackjack extends Command {
    private static final MoneyArgument BET_ARG = new MoneyArgument.Builder("bet").description("The amount to bet")
            .require()
            .get();

    private static final Map<String, BlackjackGame> games = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    public Blackjack() {
        super(BET_ARG);
    }

    @Override
    public void execute(Context ctx) {
        float bet = ctx.getArgument(BET_ARG).map(ParsedArgument::value).orElse(0f);
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
            BlackjackGame game = games.get(channelId);
            String result = game.joinPlayer(gi, bet, ctx.getMember());
            if (result != null) {
                ctx.fail(result);
            } else {
                ctx.getMessage().addReaction(Emoji.fromUnicode("\u2705")).queue();
            }
            return;
        }

        BlackjackGame game = games.computeIfAbsent(channelId, k -> new BlackjackGame((TextChannel) ctx.getChannel()));
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
            String result = game.joinPlayer(gi, finalBet, ctx.getMember());
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

    public static void handleBlackjackButton(ButtonInteractionEvent event) {
        BlackjackGame game = games.get(event.getChannelId());
        if (game == null) {
            event.reply("No active blackjack game in this channel!").setEphemeral(true).queue();
            return;
        }

        String action = event.getComponentId().split("_")[1];

        switch (action) {
            case "hit" -> game.playerHit(event.getUser().getId(), event);
            case "stand" -> game.playerStand(event.getUser().getId(), event);
            case "start" -> game.startGame(event);
            case "rejoin" -> game.playerRejoin(event.getUser().getId(), event);
            case "results" -> event.reply("Game results are shown in the game message.").setEphemeral(true).queue();
            default -> event.reply("Invalid action").setEphemeral(true).queue();
        }
    }

    private static class BlackjackGame {
        private static final long BETTING_TIME_SECONDS = 20;
        private static final long PLAYER_TIME_SECONDS = 10;

        private final TextChannel channel;
        private final List<BlackjackPlayer> players = new ArrayList<>();
        private final List<Card> deck = new ArrayList<>();
        private final List<Card> dealerHand = new ArrayList<>();
        private GameState state = GameState.WAITING;
        private Message currentGameMessage;
        private int currentPlayerIndex = -1;
        private boolean dealerRevealed = false;
        private ScheduledFuture<?> bettingTimer;
        private ScheduledFuture<?> playerTimer;
        @Setter
        private Runnable messageReadyCallback;

        private final BlackjackGame previousGame;

        public BlackjackGame(TextChannel channel) {
            this(channel, null);
        }

        public BlackjackGame(TextChannel channel, BlackjackGame previousGame) {
            this.channel = channel;
            this.previousGame = previousGame;
            initializeDeck();
            startBettingTimer();
            updateGameMessage();
        }

        private enum GameState {
            WAITING, IN_PROGRESS, DEALER_TURN, COMPLETED
        }

        private void initializeDeck() {
            deck.clear();
            for (Suit suit : Suit.values()) {
                for (Rank rank : Rank.values()) {
                    deck.add(new Card(suit, rank));
                }
            }
            Collections.shuffle(deck);
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

        public String joinPlayer(GamblingInstance gi, float bet, Member playerMember) {
            if (state != GameState.WAITING) {
                return "Game has already started!";
            }

            if (players.stream().anyMatch(p -> p.getId().equals(playerMember.getId()))) {
                return "You're already in the game!";
            }

            gi.play(bet);
            players.add(new BlackjackPlayer(gi, bet, playerMember));
            updateGameMessage();
            return null;
        }

        public void startGame(ButtonInteractionEvent event) {
            if (state != GameState.WAITING) {
                return;
            }
            if (bettingTimer != null) {
                bettingTimer.cancel(false);
            }
            state = GameState.IN_PROGRESS;

            for (int i = 0; i < 2; i++) {
                for (BlackjackPlayer player : players) {
                    player.addCard(drawCard());
                }
                dealerHand.add(drawCard());
            }

            currentPlayerIndex = 0;
            startPlayerTimer();
            updateGameMessage();

            if (event != null) {
                event.deferEdit().queue();
            }
        }

        private void startPlayerTimer() {
            if (playerTimer != null) {
                playerTimer.cancel(false);
            }
            playerTimer = executor.schedule(() -> {
                                                if (currentPlayerIndex < players.size()) {
                                                    BlackjackPlayer currentPlayer = players.get(currentPlayerIndex);
                                                    channel.sendMessage(String.format("Time's up for %s! Automatically standing.",
                                                                                      currentPlayer.getMember().getEffectiveName()
                                                    )).queue();
                                                    playerStand(players.get(currentPlayerIndex).getId(), null);
                                                }
                                            }, PLAYER_TIME_SECONDS, TimeUnit.SECONDS
            );
        }

        public void playerHit(String playerId, ButtonInteractionEvent event) {
            if (!isCurrentPlayer(playerId)) {
                if (event != null) {
                    event.reply("It's not your turn!").setEphemeral(true).queue();
                }
                return;
            }

            if (playerTimer != null) {
                playerTimer.cancel(false);
            }

            BlackjackPlayer player = players.get(currentPlayerIndex);
            player.addCard(drawCard());

            if (player.getHandValue() > 21) {
                if (event != null) {
                    event.reply("Bust! You went over 21.").setEphemeral(true).queue();
                }
                nextPlayer();
            } else {
                updateGameMessage();
                if (event != null) {
                    event.deferEdit().queue();
                }
            }
        }

        public void playerStand(String playerId, ButtonInteractionEvent event) {
            if (!isCurrentPlayer(playerId)) {
                if (event != null) {
                    event.reply("It's not your turn!").setEphemeral(true).queue();
                }
                return;
            }

            if (playerTimer != null) {
                playerTimer.cancel(false);
            }

            nextPlayer();
            if (event != null) {
                event.deferEdit().queue();
            }
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

            BlackjackPlayer previousPlayer = previousGame.players.stream()
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

        private boolean isCurrentPlayer(String playerId) {
            return state == GameState.IN_PROGRESS && currentPlayerIndex < players.size() && players.get(
                    currentPlayerIndex).getId().equals(playerId);
        }

        private void nextPlayer() {
            currentPlayerIndex++;
            if (currentPlayerIndex >= players.size()) {
                dealerTurn();
            } else {
                startPlayerTimer();
            }
            updateGameMessage();
        }

        private void dealerTurn() {
            state = GameState.DEALER_TURN;
            dealerRevealed = true;

            while (calculateHandValue(dealerHand) < 17) {
                dealerHand.add(drawCard());
            }

            endGame();
        }

        private void endGame() {
            state = GameState.COMPLETED;
            int dealerValue = calculateHandValue(dealerHand);

            for (BlackjackPlayer player : players) {
                int playerValue = player.getHandValue();
                float bet = player.getBet();

                if (playerValue > 21) {
                    player.setResult("BUST");
                } else if (dealerValue > 21) {
                    player.setResult("WIN");
                    player.win(bet * 2);
                } else if (playerValue > dealerValue) {
                    player.setResult("WIN");
                    player.win(bet * 2);
                } else if (playerValue == dealerValue) {
                    player.setResult("PUSH");
                    player.win(bet);
                } else {
                    player.setResult("LOSE");
                }
            }

            updateGameMessage();

            if (playerTimer != null) {
                playerTimer.cancel(false);
            }
            if (bettingTimer != null) {
                bettingTimer.cancel(false);
            }

            IO.getSystem(EconIO.class).save();

            games.remove(channel.getId());
            games.put(channel.getId(), new BlackjackGame(channel, this));
        }

        private Card drawCard() {
            return deck.removeFirst();
        }

        private int calculateHandValue(List<Card> hand) {
            int value = 0;
            int aces = 0;

            for (Card card : hand) {
                value += card.rank().getValue();
                if (card.rank() == Rank.ACE) {
                    aces++;
                }
            }

            while (value > 21 && aces > 0) {
                value -= 10;
                aces--;
            }

            return value;
        }

        public int getPlayerCount() {
            return players.size();
        }

        private void updateGameMessage() {
            EmbedBuilder eb = new EmbedBuilder().setTitle("♠️ BLACKJACK ♥️").setColor(0x2ECC71);

            eb.addField("Dealer's Hand",
                        formatCards(dealerHand, dealerRevealed) + (dealerRevealed ? " (Value: " + calculateHandValue(
                                dealerHand) + ")" : ""),
                        false
            );

            String currentPlayerMention = "";
            for (int i = 0; i < players.size(); i++) {
                BlackjackPlayer player = players.get(i);
                String status = (i == currentPlayerIndex && state == GameState.IN_PROGRESS) ? " ▶ YOUR TURN" : "";
                String result = player.getResult() != null ? "**" + player.getResult() + "**" : "";
                if (i == currentPlayerIndex && state == GameState.IN_PROGRESS) {
                    currentPlayerMention = player.getMember().getAsMention();
                }

                eb.addField(player.getMember().getEffectiveName() + status + " | Bet: $" + player.getBet(),
                            formatCards(player.getHand()) + " (Value: " + player.getHandValue() + ") " + result,
                            false
                );
            }

            switch (state) {
                case WAITING ->
                        eb.setDescription("Game starting in " + BETTING_TIME_SECONDS + " seconds! Use `!blackjack <bet>` to join");
                case COMPLETED -> eb.setDescription(String.format(
                        "Game finished! New game starting soon (%s seconds)...",
                        TimeUnit.SECONDS.toSeconds(BETTING_TIME_SECONDS)
                ));
            }

            List<Button> buttons = new ArrayList<>();
            if (state == GameState.WAITING) {
                buttons.add(Button.primary("blackjack_start", "Start Game"));
                if (Objects.nonNull(previousGame)) {
                    buttons.add(Button.secondary("blackjack_rejoin", "Join with previous bet")
                                      .withEmoji(Emoji.fromUnicode("🔙")));
                }
            } else if (state == GameState.IN_PROGRESS && currentPlayerIndex < players.size()) {
                buttons.add(Button.primary("blackjack_hit", "Hit").withEmoji(Emoji.fromUnicode("⬇️")));
                buttons.add(Button.danger("blackjack_stand", "Stand").withEmoji(Emoji.fromUnicode("✋")));
            } else { // TODO: fix this hack to avoid "Cannot have empty row!" error
                buttons.add(Button.secondary("blackjack_results", "View Results"));
            }

            if (currentGameMessage == null) {
                MessageCreateBuilder mb = new MessageCreateBuilder().setContent(!currentPlayerMention.isEmpty()
                                                                                ? currentPlayerMention
                                                                                : "Blackjack Game")
                                                                    .setEmbeds(eb.build())
                                                                    .setActionRow(buttons);
                channel.sendMessage(mb.build()).queue(message -> {
                    currentGameMessage = message;
                    if (messageReadyCallback != null) {
                        messageReadyCallback.run();
                        messageReadyCallback = null;
                    }
                });
            } else {
                MessageEditBuilder ebuilder = new MessageEditBuilder().setContent(!currentPlayerMention.isEmpty()
                                                                                  ? currentPlayerMention
                                                                                  : "Blackjack Game")
                                                                      .setEmbeds(eb.build())
                                                                      .setActionRow(buttons);
                currentGameMessage.editMessage(ebuilder.build()).queue(message -> currentGameMessage = message);
            }
        }

        private String formatCards(List<Card> cards, boolean revealAll) {
            return cards.stream()
                        .map(card -> revealAll || card != cards.getFirst() ? card.toString() : "❓")
                        .collect(Collectors.joining(" "));
        }

        private String formatCards(List<Card> cards) {
            return cards.stream().map(Card::toString).collect(Collectors.joining(" "));
        }
    }

    @Data
    private static class BlackjackPlayer {
        private final GamblingInstance gi;
        private final float bet;
        private final Member member;
        private final String id;
        private final List<Card> hand = new ArrayList<>();
        private String result;

        public BlackjackPlayer(GamblingInstance gi, float bet, Member member) {
            this.gi = gi;
            this.bet = bet;
            this.member = member;
            this.id = member.getId();
        }

        public void addCard(Card card) {
            hand.add(card);
        }

        public int getHandValue() {
            int value = 0;
            int aces = 0;

            for (Card card : hand) {
                value += card.rank().getValue();
                if (card.rank() == Rank.ACE) {
                    aces++;
                }
            }

            while (value > 21 && aces > 0) {
                value -= 10;
                aces--;
            }

            return value;
        }

        public void win(float amount) {
            gi.win(amount);
        }
    }
}