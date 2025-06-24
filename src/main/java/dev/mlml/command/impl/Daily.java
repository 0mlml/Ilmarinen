package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.systems.economy.EconIO;
import dev.mlml.systems.economy.EconUser;
import dev.mlml.systems.economy.EconomySystem;
import dev.mlml.systems.IO;
import net.dv8tion.jda.api.Permission;

@CommandInfo(
        keywords = {"daily"},
        name = "Daily",
        description = "Get your daily money",
        permissions = {Permission.MESSAGE_SEND},
        cooldown = 60 * 60 * 24,
        category = CommandInfo.Category.Economy
)
public class Daily extends Command {
    private static final int daily = 20;

    @Override
    public void execute(Context ctx) {
        EconUser econUser = EconomySystem.getUser(ctx.getMember().getId());
        econUser.addMoney(daily);

        ctx.succeed(String.format("You got your daily %d money", daily));
        IO.getSystem(EconIO.class).save();
    }
}
