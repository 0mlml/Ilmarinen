package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.argument.MoneyArgument;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.command.argument.UserArgument;
import dev.mlml.systems.IO;
import dev.mlml.systems.economy.EconIO;
import dev.mlml.systems.economy.EconUser;
import dev.mlml.systems.economy.EconomySystem;
import net.dv8tion.jda.api.entities.User;

@CommandInfo(
        keywords = {"pay", "transfer", "give"},
        name = "Pay",
        description = "Pay (economy)",
        category = CommandInfo.Category.Economy
)
public class Pay extends Command {
    private static final UserArgument USER_ARGUMENT = new UserArgument.Builder("user")
            .description("The user to pay the money to")
            .require()
            .get();
    private static final MoneyArgument MONEY_ARGUMENT = new MoneyArgument.Builder("money")
            .description("The amount of money to pay")
            .require()
            .get();


    public Pay() {
        super(USER_ARGUMENT, MONEY_ARGUMENT);
    }

    @Override
    public void execute(Context ctx) {
        User user = ctx.getArgument(USER_ARGUMENT).map(ParsedArgument::value).orElse(null);
        float money = ctx.getArgument(MONEY_ARGUMENT).map(ParsedArgument::value).orElse(0f);

        if (user == null) {
            ctx.fail("Invalid user");
            return;
        }

        if (money <= 0) {
            ctx.fail("Invalid money");
            return;
        }

        EconUser sender = EconomySystem.getUser(ctx.getMember().getId());
        EconUser recipient = EconomySystem.getUser(user.getId());

        if (!sender.canAfford(money)) {
            ctx.fail("You do not have enough money to pay this amount!");
            return;
        }

        sender.addMoney(-money);
        recipient.addMoney(money);


        ctx.succeed(String.format("Successfully paid <@%s> $%.2f!", user.getId(), money));

        IO.getSystem(EconIO.class).save();
    }
}

