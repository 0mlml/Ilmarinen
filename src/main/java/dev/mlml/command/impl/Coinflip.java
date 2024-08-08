package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.argument.MoneyArgument;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.command.argument.StringArgument;
import dev.mlml.economy.*;

@CommandInfo(
        keywords = {"coinflip", "cf"},
        name = "Coinflip",
        description = "Flip a coin",
        category = CommandInfo.Category.Economy
)
public class Coinflip extends Command {
    private static final StringArgument SIDE_ARG = new StringArgument.Builder("side").description(
            "The side of the coin to bet on").require().get();
    private static final MoneyArgument AMOUNT_ARG = new MoneyArgument.Builder("amount").description(
            "The amount of money to bet").require().get();

    public Coinflip() {
        super(SIDE_ARG, AMOUNT_ARG);
    }

    private static String invert(String side) {
        return side.equals("heads") ? "tails" : "heads";
    }

    @Override
    public void execute(Context ctx) {
        float amount = ctx.getArgument(AMOUNT_ARG).map(ParsedArgument::getValue).orElse(0f);
        String side = ctx.getArgument(SIDE_ARG).map(ParsedArgument::getValue).orElse("");

        side = side.toLowerCase();

        if (!side.equals("heads") && !side.equals("tails")) {
            ctx.fail("Invalid side");
            return;
        }

        EconGuild eg = Economy.getGuild(ctx.getGuild().getId());
        EconUser eu = Economy.getUser(ctx.getMember().getId());

        GamblingInstance gi = new GamblingInstance(eu, eg);

        if (amount >= Float.MAX_VALUE) {
            amount = eu.getMoney();
        }

        if (!eu.canAfford(amount)) {
            ctx.fail("You don't have enough money");
            return;
        }

        gi.play(amount);

        float random = (float) Math.random();
        StringBuilder sb = new StringBuilder();
        sb.append("The coin landed on ");
        if (random < 0.5) {
            sb.append(side);
            sb.append("! You won ");
            sb.append(amount * 2);
            gi.win(amount * 2);
            ctx.succeed(sb.toString());
        } else {
            sb.append(invert(side));
            sb.append("! You lost!");
            ctx.inform(sb.toString());
        }

        IO.save();
    }
}
