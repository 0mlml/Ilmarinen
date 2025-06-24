package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.Replies;
import dev.mlml.command.argument.MoneyArgument;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.systems.IO;
import dev.mlml.systems.economy.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@CommandInfo(
        keywords = {"slotmachine", "sm"},
        name = "Slotmachine",
        description = "Play the slot machine",
        category = CommandInfo.Category.Economy,
        cooldown = 5
)
public class Slotmachine extends Command {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(Slotmachine.class);

    private static final MoneyArgument AMOUNT_ARG = new MoneyArgument.Builder("amount")
            .description("The amount of money to bet")
            .get();

    private static final String ROLLING_EMOJI = "<a:slots_roll:948603881087180800>";

    private static final List<Payout> PAYOUTS = List.of(
            new Payout(":lemon:", 8, 0.1),
            new Payout(":strawberry:", 6, 0.2),
            new Payout(":apple:", 4, 0.3),
            new Payout(":cherries:", 2, 0.4)
    );

    public Slotmachine() {
        super(AMOUNT_ARG);
    }

    @Override
    public void execute(Context ctx) {
        Optional<ParsedArgument<Float>> amountArg = ctx.getArgument(AMOUNT_ARG);

        EmbedBuilder resEmbed = Replies.base(ctx).setColor(0x00FF00);

        if (amountArg.isEmpty()) {
            displayPayouts(resEmbed);
            ctx.reply(resEmbed.build());
            return;
        }

        EconUser eu = EconomySystem.getUser(ctx.getMember().getId());
        EconGuild eg = EconomySystem.getGuild(ctx.getGuild().getId());
        GamblingInstance gi = new GamblingInstance(eu, eg);

        float amount = amountArg.get().getValue() >= Float.MAX_VALUE ? eu.getMoney() : amountArg.get().getValue();

        if (!eu.canAfford(amount)) {
            ctx.fail("You don't have enough money");
            return;
        }

        gi.play(amount);
        List<Payout> rolled = new ArrayList<>();
        resEmbed.setDescription(ROLLING_EMOJI.repeat(3));
        MessageEmbed initialEmbed = resEmbed.build();

        ctx.getMessage().replyEmbeds(initialEmbed).queue(resMsg -> {
            for (int i = 0; i < 3; i++) {
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    logger.error("Interrupted while sleeping", e);
                }
                rolled.add(roll());
                resEmbed.setDescription(getRolledEmojis(rolled) + ROLLING_EMOJI.repeat(3 - rolled.size()));
                resMsg.editMessageEmbeds(resEmbed.build()).queue();
            }

            float winnings = calculateWinnings(amount, rolled);
            if (winnings > 0) {
                gi.win(winnings);
                resEmbed.appendDescription("\nYou won " + winnings + "! (Payout: " + winnings / amount + "x" + amount + ")");
            } else {
                resEmbed.appendDescription("\nYou lost " + amount + "!");
            }

            resMsg.editMessageEmbeds(resEmbed.build()).queue();
            IO.getSystem(EconIO.class).save();
        });
    }

    private void displayPayouts(EmbedBuilder resEmbed) {
        for (Payout payout : PAYOUTS) {
            resEmbed.addField(payout.emoji().repeat(3), "x" + payout.payoutMult(), true);
        }
    }

    private Payout roll() {
        double randomValue = new Random().nextDouble();
        for (Payout payout : PAYOUTS) {
            if (randomValue < payout.probability()) {
                return payout;
            }
            randomValue -= payout.probability();
        }
        return PAYOUTS.get(PAYOUTS.size() - 1); // fallback to the last item
    }

    private String getRolledEmojis(List<Payout> rolled) {
        StringBuilder sb = new StringBuilder();
        for (Payout payout : rolled) {
            sb.append(payout.emoji());
        }
        return sb.toString();
    }

    private float calculateWinnings(float amount, List<Payout> rolled) {
        if (rolled.get(0).equals(rolled.get(1)) && rolled.get(0).equals(rolled.get(2))) {
            return rolled.get(0).payoutMult() * amount;
        }
        return 0;
    }

    private record Payout(String emoji, int payoutMult, double probability) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Payout payout = (Payout) obj;
            return emoji.equals(payout.emoji);
        }

        @Override
        public int hashCode() {
            return emoji.hashCode();
        }
    }
}
