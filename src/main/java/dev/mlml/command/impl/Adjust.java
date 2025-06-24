package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.argument.BooleanArgument;
import dev.mlml.command.argument.MoneyArgument;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.command.argument.UserArgument;
import dev.mlml.systems.IO;
import dev.mlml.systems.economy.EconIO;
import dev.mlml.systems.economy.EconUser;
import dev.mlml.systems.economy.EconomySystem;
import net.dv8tion.jda.api.entities.User;

@CommandInfo(
        keywords = {"adjust", "adj"},
        name = "Adjust",
        description = "Adjust the money of the player",
        extendedPermissions = {CommandInfo.ExtendedPermission.BOT_ADMIN}
)
public class Adjust extends Command {
    private static final UserArgument USER_ARGUMENT = new UserArgument.Builder("user")
            .description("The user to adjust the money of")
            .require()
            .get();
    private static final MoneyArgument MONEY_ARGUMENT = new MoneyArgument.Builder("money")
            .description("The amount of money to adjust")
            .require()
            .get();
    private static final BooleanArgument SET_MONEY = new BooleanArgument.Builder("set")
            .description("If true, sets the money to the specified amount, otherwise adds the amount")
            .get();

    public Adjust() {
        super(USER_ARGUMENT, MONEY_ARGUMENT, SET_MONEY);
    }

    @Override
    public void execute(Context ctx) {
        User user = ctx.getArgument(USER_ARGUMENT).map(ParsedArgument::getValue).orElse(null);
        float money = ctx.getArgument(MONEY_ARGUMENT).map(ParsedArgument::getValue).orElse(0f);
        boolean set = ctx.getArgument(SET_MONEY).map(ParsedArgument::getValue).orElse(false);

        if (user == null) {
            ctx.fail("Invalid user");
            return;
        }

        if (money <= 0) {
            ctx.fail("Invalid money");
            return;
        }

        EconUser eu = EconomySystem.getUser(user.getId());

        if (set) {
            eu.setMoney(money);
        } else {
            eu.addMoney(money);
        }

        ctx.succeed(String.format("Adjusted money of %s by %f, now %.2f",
                                  user.getEffectiveName(),
                                  money,
                                  eu.getMoney()
        ));

        IO.getSystem(EconIO.class).save();
    }
}
