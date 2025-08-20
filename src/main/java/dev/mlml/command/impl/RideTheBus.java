package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.argument.MoneyArgument;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.systems.IO;
import dev.mlml.systems.economy.*;
import dev.mlml.util.CardDeck.Card;
import dev.mlml.util.CardDeck.Rank;
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
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;

@CommandInfo(
        keywords = {"ridethebus", "rtb"},
        name = "RideTheBus",
        description = "Play a game of Ride the Bus",
        category = CommandInfo.Category.Economy
)
public class RideTheBus extends Command {
    private static final MoneyArgument BET_ARG = new MoneyArgument.Builder("bet").description("The amount to bet")
            .require()
            .get();

    private static final Map<String, RideTheBusGame> games = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    private static final long STAGE_TIME_SECONDS = 10;
    private static final long WAITING_TIME_SECONDS = 20;

    public RideTheBus() {
        super(BET_ARG);
    }

    @Override
    public void execute(Context ctx) {
        float bet = ctx.getArgument(BET_ARG)
                       .map(ParsedArgument::value)
                       .orElse(0f);
        String channelId = ctx.getChannel()
                              .getId();

        if (bet <= 0) {
            ctx.fail("Bet amount must be positive!");
            return;
        }

        EconUser eu = EconomySystem.getUser(ctx.getMember()
                                               .getId());
        EconGuild eg = EconomySystem.getGuild(ctx.getGuild()
                                                 .getId());
        GamblingInstance gi = new GamblingInstance(eu, eg);

        if (bet >= Float.MAX_VALUE) {
            bet = eu.getMoney();
        }

        if (!eu.canAfford(bet)) {
            ctx.fail("You don't have enough money!");
            return;
        }

        if (games.containsKey(channelId)) {
            RideTheBusGame game = games.get(channelId);
            String result = game.joinPlayer(gi, bet, ctx.getMember());
            if (result != null) {
                ctx.fail(result);
            } else {
                ctx.getMessage()
                   .addReaction(Emoji.fromUnicode("\u2705"))
                   .queue();
            }
            return;
        }

        RideTheBusGame game = games.computeIfAbsent(channelId, k -> new RideTheBusGame((TextChannel) ctx.getChannel()));
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
                      ctx.getMessage()
                         .addReaction(Emoji.fromUnicode("\u2705"))
                         .queue();
                  }
              })
              .exceptionally(e -> {
                  ctx.fail("An error occurred while waiting for the game to start: " + e.getMessage());
                  return null;
              });
    }

    public static void handleRideTheBusButton(ButtonInteractionEvent event) {
        RideTheBusGame game = games.get(event.getChannelId());
        if (game == null) {
            event.reply("No active Ride the Bus game in this channel!")
                 .setEphemeral(true)
                 .queue();
            return;
        }

        String[] parts = event.getComponentId()
                              .split("_");
        String action = parts[1];
        String playerId = event.getUser()
                               .getId();

        switch (action) {
            case "red" -> game.playerChoice(playerId, Choice.RED, event);
            case "black" -> game.playerChoice(playerId, Choice.BLACK, event);
            case "higher" -> game.playerChoice(playerId, Choice.HIGHER, event);
            case "lower" -> game.playerChoice(playerId, Choice.LOWER, event);
            case "inside" -> game.playerChoice(playerId, Choice.INSIDE, event);
            case "outside" -> game.playerChoice(playerId, Choice.OUTSIDE, event);
            case "hearts" -> game.playerChoice(playerId, Choice.HEARTS, event);
            case "diamonds" -> game.playerChoice(playerId, Choice.DIAMONDS, event);
            case "clubs" -> game.playerChoice(playerId, Choice.CLUBS, event);
            case "spades" -> game.playerChoice(playerId, Choice.SPADES, event);
            case "cashout" -> game.playerCashOut(playerId, event);
            case "start" -> game.startGame(event);
            case "rejoin" -> game.playerRejoin(event.getUser()
                                                    .getId(), event
            );
            default -> event.reply("Invalid action")
                            .setEphemeral(true)
                            .queue();
        }
    }

    private enum Choice {
        RED, BLACK, HIGHER, LOWER, INSIDE, OUTSIDE, HEARTS, DIAMONDS, CLUBS, SPADES
    }

    private static class RideTheBusGame {
        private final TextChannel channel;
        private final List<BusPlayer> players = new ArrayList<>();
        private final List<Card> cards = new ArrayList<>();
        private GameState state = GameState.WAITING;
        private Message currentGameMessage;
        private int currentStage = -1;
        private final Map<String, Choice> currentChoices = new HashMap<>();
        private final Map<String, Float> cashOutMultipliers = new HashMap<>();
        private final float[] stageMultipliers = {2f, 3f, 4f, 20f};

        private ScheduledFuture<?> stageTimer;
        @Setter
        private Runnable messageReadyCallback;

        private final RideTheBusGame previousGame;

        public RideTheBusGame(TextChannel channel) {
            this(channel, null);
        }

        public RideTheBusGame(TextChannel channel, RideTheBusGame previousGame) {
            this.channel = channel;
            this.previousGame = previousGame;
            initializeDeck();
            startWaitingTimer();
            updateGameMessage();
        }

        private enum GameState {
            WAITING, IN_PROGRESS, COMPLETED
        }

        private void initializeDeck() {
            List<Card> deck = new ArrayList<>();
            for (Suit suit : Suit.values()) {
                for (Rank rank : Rank.values()) {
                    deck.add(new Card(suit, rank));
                }
            }
            Collections.shuffle(deck);
            cards.addAll(deck.subList(0, 4));
        }

        private void startWaitingTimer() {
            stageTimer = executor.schedule(() -> {
                                               if (!players.isEmpty()) {
                                                   startGame(null);
                                               } else {
                                                   currentGameMessage.editMessage("Game cancelled due to no players joining.")
                                                                     .queue();
                                                   games.remove(channel.getId());
                                               }
                                           }, WAITING_TIME_SECONDS, TimeUnit.SECONDS
            );
        }

        public String joinPlayer(GamblingInstance gi, float bet, Member playerMember) {
            if (state != GameState.WAITING) {
                if (players.isEmpty()) {
                    endGame();
                }
                return "Game has already started!";
            }

            if (players.stream()
                       .anyMatch(p -> p.getId()
                                       .equals(playerMember.getId()))) {
                return "You're already in the game!";
            }

            gi.play(bet);
            players.add(new BusPlayer(gi, bet, playerMember));
            updateGameMessage();
            return null;
        }

        public void playerRejoin(String playerId, ButtonInteractionEvent event) {
            if (state != GameState.WAITING) {
                if (event != null) {
                    event.reply("Game is already in progress!")
                         .setEphemeral(true)
                         .queue();
                }
                return;
            }

            if (players.stream()
                       .anyMatch(p -> p.getId()
                                       .equals(playerId))) {
                if (event != null) {
                    event.reply("You are already in the game!")
                         .setEphemeral(true)
                         .queue();
                }
                return;
            }

            if (Objects.isNull(previousGame)) {
                if (event != null) {
                    event.reply("No previous game to rejoin!")
                         .setEphemeral(true)
                         .queue();
                }
                return;
            }

            BusPlayer previousPlayer = previousGame.players.stream()
                                                           .filter(p -> p.getId()
                                                                         .equals(playerId))
                                                           .findFirst()
                                                           .orElse(null);

            if (previousPlayer == null) {
                if (event != null) {
                    event.reply("You weren't in the previous game!")
                         .setEphemeral(true)
                         .queue();
                }
                return;
            }

            if (!previousPlayer.getGi()
                               .user()
                               .canAfford(previousPlayer.getBet())) {
                if (event != null) {
                    event.reply("You can't afford to rejoin with your previous bet!")
                         .setEphemeral(true)
                         .queue();
                }
                return;
            }

            joinPlayer(previousPlayer.getGi(), previousPlayer.getBet(), previousPlayer.getMember());
            updateGameMessage();

            if (event != null) {
                event.deferEdit()
                     .queue();
            }
        }

        public void startGame(ButtonInteractionEvent event) {
            if (state != GameState.WAITING) {
                return;
            }
            if (Objects.isNull(event) && !players.stream()
                                                 .anyMatch(p -> p.getMember()
                                                                 .getId()
                                                                 .equals(event.getUser()
                                                                              .getId()))) {
                event.reply("You are not in the game!")
                     .setEphemeral(true)
                     .queue();
                return;
            }
            if (players.isEmpty()) {
                if (event != null) {
                    event.reply("No players have joined!")
                         .setEphemeral(true)
                         .queue();
                }
                return;
            }
            state = GameState.IN_PROGRESS;
            currentStage = 0;
            startStageTimer();
            updateGameMessage();

            if (event != null) {
                event.deferEdit()
                     .queue();
            }
        }

        private void startStageTimer() {
            stageTimer = executor.schedule(() -> {
                                               channel.sendMessage("Time's up for stage " + (currentStage + 1) + "!")
                                                      .queue();
                                               processStageCompletion();
                                           }, WAITING_TIME_SECONDS, TimeUnit.SECONDS
            );
        }

        public void playerChoice(String playerId, Choice choice, ButtonInteractionEvent event) {
            if (state != GameState.IN_PROGRESS) {
                if (event != null) {
                    event.reply("Game is not in progress!")
                         .setEphemeral(true)
                         .queue();
                }
                return;
            }

            BusPlayer player = players.stream()
                                      .filter(p -> p.getId()
                                                    .equals(playerId) && !p.isEliminated() && !p.hasCashedOut())
                                      .findFirst()
                                      .orElse(null);

            if (player == null) {
                if (event != null) {
                    event.reply("You're not in the game or already cashed out!")
                         .setEphemeral(true)
                         .queue();
                }
                return;
            }

            currentChoices.put(playerId, choice);
            updateGameMessage();

            if (event != null) {
                event.deferEdit()
                     .queue();
            }

            long activePlayers = players.stream()
                                        .filter(p -> !p.isEliminated() && !p.hasCashedOut())
                                        .count();
            if (currentChoices.size() >= activePlayers) {
                processStageCompletion();
            }
        }

        public void playerCashOut(String playerId, ButtonInteractionEvent event) {
            if (state != GameState.IN_PROGRESS) {
                if (event != null) {
                    event.reply("Game is not in progress!")
                         .setEphemeral(true)
                         .queue();
                }
                return;
            }

            BusPlayer player = players.stream()
                                      .filter(p -> p.getId()
                                                    .equals(playerId) && !p.isEliminated() && !p.hasCashedOut())
                                      .findFirst()
                                      .orElse(null);

            if (player == null) {
                if (event != null) {
                    event.reply("You're not in the game or already cashed out!")
                         .setEphemeral(true)
                         .queue();
                }
                return;
            }

            float multiplier = player.getCurrentMultiplier();
            cashOutMultipliers.put(playerId, multiplier);
            player.cashOut(multiplier);
            updateGameMessage();

            if (event != null) {
                event.reply("Cashed out with " + multiplier + "x multiplier!")
                     .setEphemeral(true)
                     .queue();
            }

            if (players.stream()
                       .allMatch(p -> p.isEliminated() || p.hasCashedOut())) {
                endGame();
            }
        }

        private void processStageCompletion() {
            if (stageTimer != null) {
                stageTimer.cancel(false);
            }
            for (BusPlayer player : players) {
                if (player.isEliminated() || player.hasCashedOut()) {
                    continue;
                }

                Choice choice = currentChoices.get(player.getId());
                if (choice == null) {
                    cashOutMultipliers.put(player.getId(), player.getCurrentMultiplier());
                    player.cashOut(player.getCurrentMultiplier());
                    continue;
                }

                boolean correct = checkAnswer(currentStage, choice);
                if (correct) {
                    player.advanceStage(stageMultipliers[currentStage]);
                } else {
                    player.eliminate();
                }
            }

            currentChoices.clear();
            currentStage++;

            if (currentStage >= 4 || players.stream()
                                            .allMatch(p -> p.isEliminated() || p.hasCashedOut())) {
                endGame();
            } else {
                startStageTimer();
                updateGameMessage();
            }
        }

        private boolean checkAnswer(int stage, Choice choice) {
            switch (stage) {
                case 0: // Red or Black
                    Suit suit = cards.getFirst()
                                     .suit();
                    return (choice == Choice.RED && (suit == Suit.HEARTS || suit == Suit.DIAMONDS)) || (choice == Choice.BLACK && (suit == Suit.CLUBS || suit == Suit.SPADES));
                case 1: // Higher or Lower
                    int firstValue = cards.get(0)
                            .rank()
                            .getValue();
                    int secondValue = cards.get(1)
                            .rank()
                            .getValue();
                    return (choice == Choice.HIGHER && secondValue > firstValue) || (choice == Choice.LOWER && secondValue < firstValue);
                case 2: // Inside or Outside
                    int card1 = cards.get(0)
                            .rank()
                            .getValue();
                    int card2 = cards.get(1)
                            .rank()
                            .getValue();
                    int card3 = cards.get(2)
                            .rank()
                            .getValue();
                    int low = Math.min(card1, card2);
                    int high = Math.max(card1, card2);
                    return (choice == Choice.INSIDE && card3 > low && card3 < high) || (choice == Choice.OUTSIDE && (card3 < low || card3 > high));
                case 3: // Suit
                    Suit cardSuit = cards.get(3)
                            .suit();
                    return (choice == Choice.HEARTS && cardSuit == Suit.HEARTS) || (choice == Choice.DIAMONDS && cardSuit == Suit.DIAMONDS) || (choice == Choice.CLUBS && cardSuit == Suit.CLUBS) || (choice == Choice.SPADES && cardSuit == Suit.SPADES);
                default:
                    return false;
            }
        }

        private void endGame() {
            state = GameState.COMPLETED;

            for (BusPlayer player : players) {
                if (!player.isEliminated() && !player.hasCashedOut()) {
                    float multiplier = player.getCurrentMultiplier() * stageMultipliers[3];
                    player.win(multiplier);
                    cashOutMultipliers.put(player.getId(), multiplier);
                }
            }

            updateGameMessage();

            if (stageTimer != null) {
                stageTimer.cancel(false);
            }

            IO.getSystem(EconIO.class)
              .save();

            games.remove(channel.getId());
            games.put(channel.getId(), new RideTheBusGame(channel, this));
        }

        private void updateGameMessage() {
            EmbedBuilder eb = new EmbedBuilder().setTitle("🚌 RIDE THE BUS 🚌")
                                                .setColor(0x3498DB);

            String stageDescription = getStageDescription();
            eb.setDescription(stageDescription);

            if (state != GameState.WAITING) {
                StringBuilder cardsField = new StringBuilder();
                for (int i = 0; i < cards.size(); i++) {
                    if (i < currentStage) {
                        cardsField.append(cards.get(i)
                                                  .toString());
                    } else {
                        cardsField.append("[❓]");
                    }
                    cardsField.append(" ");
                }
                eb.addField("Cards", cardsField.toString(), false);
            }

            for (BusPlayer player : players) {
                String status;
                if (player.isEliminated()) {
                    status = "❌ ELIMINATED";
                } else if (player.hasCashedOut()) {
                    float multiplier = cashOutMultipliers.getOrDefault(player.getId(), 1f);
                    status = "💰 CASHED OUT (" + multiplier + "x)";
                } else {
                    status = "Stage " + player.getCurrentStage() + " | Multiplier: " + player.getCurrentMultiplier() + "x";
                    if (currentChoices.containsKey(player.getId())) {
                        status += " | Choice: " + currentChoices.get(player.getId());
                    }
                }

                eb.addField(player.getMember()
                                  .getEffectiveName() + " | Bet: $" + player.getBet(), status, false
                );
            }

            List<Button> buttons = new ArrayList<>();
            if (state == GameState.WAITING) {
                buttons.add(Button.primary("ridethebus_start", "Start Game"));
                if (Objects.nonNull(previousGame)) {
                    buttons.add(Button.secondary("ridethebus_rejoin", "Join with previous bet")
                                      .withEmoji(Emoji.fromUnicode("🔙")));
                }
            } else if (state == GameState.IN_PROGRESS) {
                buttons.add(Button.danger("ridethebus_cashout", "Cash Out")
                                  .withEmoji(Emoji.fromUnicode("💰")));

                switch (currentStage) {
                    case 0 -> {
                        buttons.add(Button.primary("ridethebus_red", "Red"));
                        buttons.add(Button.primary("ridethebus_black", "Black"));
                    }
                    case 1 -> {
                        buttons.add(Button.primary("ridethebus_higher", "Higher"));
                        buttons.add(Button.primary("ridethebus_lower", "Lower"));
                    }
                    case 2 -> {
                        buttons.add(Button.primary("ridethebus_inside", "Inside"));
                        buttons.add(Button.primary("ridethebus_outside", "Outside"));
                    }
                    case 3 -> {
                        buttons.add(Button.primary("ridethebus_hearts", "♥"));
                        buttons.add(Button.primary("ridethebus_diamonds", "♦"));
                        buttons.add(Button.primary("ridethebus_clubs", "♣"));
                        buttons.add(Button.primary("ridethebus_spades", "♠"));
                    }
                }
            }

            if (currentGameMessage == null) {
                MessageCreateBuilder mb = new MessageCreateBuilder().setEmbeds(eb.build())
                                                                    .setActionRow(buttons);
                channel.sendMessage(mb.build())
                       .queue(message -> {
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
                currentGameMessage.editMessage(ebuilder.build())
                                  .queue(message -> currentGameMessage = message);
            }
        }

        @NotNull
        private String getStageDescription() {
            String stageDescription;
            if (state == GameState.WAITING) {
                stageDescription = "Game starting in " + WAITING_TIME_SECONDS + " seconds! Use `!ridethebus <bet>` to join";
            } else if (state == GameState.IN_PROGRESS) {
                stageDescription = "**Current Stage:** " + getStageName(currentStage) + "\nTime remaining: " + STAGE_TIME_SECONDS + " seconds";
            } else {
                stageDescription = "Game finished!";
            }
            return stageDescription;
        }

        private String getStageName(int stage) {
            return switch (stage) {
                case 0 -> "Red or Black? (2x)";
                case 1 -> "Higher or Lower? (3x)";
                case 2 -> "Inside or Outside? (4x)";
                case 3 -> "Suit? (20x)";
                default -> "Completed";
            };
        }
    }

    @Data
    private static class BusPlayer {
        private final GamblingInstance gi;
        private final float bet;
        private final Member member;
        private final String id;
        private int currentStage = 0;
        private float currentMultiplier = 1f;
        private boolean eliminated = false;
        private boolean cashedOut = false;

        public BusPlayer(GamblingInstance gi, float bet, Member member) {
            this.gi = gi;
            this.bet = bet;
            this.member = member;
            this.id = member.getId();
        }

        public void advanceStage(float multiplier) {
            currentMultiplier = multiplier;
            currentStage++;
        }

        public void cashOut(float multiplier) {
            gi.win(bet * multiplier);
            cashedOut = true;
        }

        public void win(float multiplier) {
            gi.win(bet * multiplier);
        }

        public void eliminate() {
            eliminated = true;
        }

        public boolean hasCashedOut() {
            return cashedOut;
        }
    }
}