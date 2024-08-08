package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.Replies;
import dev.mlml.command.argument.MoneyArgument;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.economy.EconGuild;
import dev.mlml.economy.EconUser;
import dev.mlml.economy.Economy;
import dev.mlml.economy.GamblingInstance;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@CommandInfo(
        keywords = {"crossyroad", "cr", "crossy"},
        name = "Crossy road",
        description = "Play cross road",
        category = CommandInfo.Category.Economy
)
public class CrossyRoad extends Command {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(CrossyRoad.class);

    private static final MoneyArgument AMOUNT_ARG = new MoneyArgument.Builder("amount").description(
                    "The amount of money to bet")
            .require()
            .get();

    public CrossyRoad() {
        super(AMOUNT_ARG);
    }

    public static void handleCrossButton(ButtonInteractionEvent event) {
        if (Objects.isNull(event.getButton().getId())) {
            return;
        }

        CrossyGame cg = CrossyGame.games.get(event.getUser().getId());
        if (Objects.isNull(cg)) {
            event.reply("You are not playing a game.").setEphemeral(true).queue();
            return;
        }

        if (!cg.isOwner(event.getUser().getId())) {
            event.reply("You are not the owner of this game.").setEphemeral(true).queue();
            return;
        }

        switch (event.getButton().getId()) {
            case "crossy_road_cross":
                cg.goNext(event);
                break;
            case "crossy_road_cashout":
                cg.cashOut();
                event.reply("You cashed out!").setEphemeral(true).queue();
                break;
            default:
                break;
        }
    }

    @Override
    public void execute(Context ctx) {
        float amount = ctx.getArgument(AMOUNT_ARG).map(ParsedArgument::getValue).orElse(0f);

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

        if (CrossyGame.games.containsKey(gi.getUser().getId())) {
            ctx.fail("You are already playing a game!");
            return;
        }

        new CrossyGame(ctx, gi, amount);
    }

    public static class CrossyGame {
        public final static Map<String, CrossyGame> games = new ConcurrentHashMap<>();

        private final GamblingInstance gi;
        private final float amount;
        private float multiplier;

        private int currentSpot = 0;
        private final int crashSpot;
        private boolean crashed = false;

        private final EmbedBuilder eb;
        private final Message message;

        public CrossyGame(Context ctx, GamblingInstance gi, float amount) {
            this.gi = gi;

            this.amount = amount;
            this.multiplier = getMultiplier(0);
            this.crashSpot = genCrashSpot();

            gi.play(amount);

            games.put(gi.getUser().getId(), this);

            eb = Replies.base(ctx);
            eb.setTitle("Crossy road");
            eb.setDescription(getPlayingState());
            eb.setColor(0x8888ff);

            message = ctx.reply(eb.build());
            message.editMessageComponents()
                   .setActionRow(Button.primary("crossy_road_cross", "Cross"),
                                 Button.danger("crossy_road_cashout", "Cash out")
                   )
                   .queue();
        }

        public static float getMultiplier(int spot) {
            return (float) Math.pow(1.2, spot);
        }

        private int genCrashSpot() {
            double p = 0.2; // probability of advancing to the next spot
            return (int) Math.floor(Math.log(1 - Math.random()) / Math.log(1 - p)) + 1;
        }

        public void goNext(ButtonInteractionEvent event) {
            currentSpot++;

            if (currentSpot == crashSpot) {
                crashed = true;
                eb.setDescription(getResults());
                event.editMessageEmbeds(eb.build()).and(event.getMessage().editMessageComponents()).queue();

                games.remove(gi.getUser().getId());
                return;
            }

            multiplier = getMultiplier(currentSpot);
            eb.setDescription(getPlayingState());
            event.editMessageEmbeds(eb.build()).queue();
        }

        public void cashOut() {
            if (Objects.isNull(message)) {
                logger.error("Message is null");
                return;
            }

            float winnings = amount * multiplier;
            gi.win(winnings);

            eb.setDescription(getResults());
            eb.setColor(0x00ff00);

            message.editMessageEmbeds(eb.build()).and(message.editMessageComponents()).queue();

            games.remove(gi.getUser().getId());
        }

        public boolean isOwner(String userId) {
            return gi.getUser().getId().equals(userId);
        }

        public String getPlayingState() {
            StringBuilder sb = new StringBuilder();
            sb.append("Cross the road and get the reward!\n");
            sb.append("You can cash out at any time, but if you crash, you lose everything!\n");
            sb.append(String.format("Multiplier: %.2f\n", getMultiplier(currentSpot)));

            String[] game = new String[10];
            game[0] = "🏠";

            int visualSpot = Math.min(currentSpot, 7);
            game[visualSpot] = "🐔";

            int cursor = 1;
            while (cursor < visualSpot) {
                game[cursor++] = "✅";
            }
            cursor = 9;
            while (cursor > visualSpot) {
                game[cursor--] = "❓";
            }

            sb.append(String.join(" ", game));

            return sb.toString();
        }

        public String getResults() {
            StringBuilder sb = new StringBuilder();
            if (crashed) {
                sb.append("You crashed!\n");
                sb.append(String.format("Multiplier: %.2f\n", getMultiplier(currentSpot - 1)));
                sb.append(String.format("Maximum multiplier was: %.2f (%d advances)\n",
                                        getMultiplier(crashSpot - 1),
                                        crashSpot - 1
                ));

                String[] game = new String[10];
                game[0] = "🏠";

                int visualSpot = Math.min(currentSpot, 7);
                game[visualSpot] = "💥";

                int cursor = 1;
                while (cursor < visualSpot) {
                    game[cursor++] = "✅";
                }
                cursor = 9;
                while (cursor > visualSpot) {
                    game[cursor--] = "❌";
                }

                sb.append(String.join(" ", game));
            } else {
                float winnings = amount * multiplier;
                sb.append("You cashed out!\n");
                sb.append(String.format("You won $%.2f at x%.2f (%d spots)\n", winnings, multiplier, currentSpot));
                sb.append(String.format("Maximum multiplier was: %.2f (%d advances)\n",
                                        getMultiplier(crashSpot - 1),
                                        crashSpot - 1
                ));

                String[] game = new String[10];
                game[0] = "🏠";

                int chickenSpot = Math.min(currentSpot, 7);
                game[chickenSpot] = "🐔";

                int cursor = 1;
                while (cursor < chickenSpot) {
                    game[cursor++] = "✅";
                }
                int crashOffsetFromChicken = crashSpot - currentSpot;
                if (chickenSpot != 0) {
                    cursor++;
                }
                while (cursor < crashOffsetFromChicken + chickenSpot && cursor < 10) {
                    game[cursor++] = "✅";
                }
                if (cursor < 10) {
                    game[cursor++] = "💥";
                    while (cursor < 10) {
                        game[cursor++] = "❌";
                    }
                }

                sb.append(String.join(" ", game));
            }

            return sb.toString();
        }
    }
}
