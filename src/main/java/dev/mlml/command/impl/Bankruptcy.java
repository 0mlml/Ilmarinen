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
        keywords = {"bankruptcy", "imbroke"},
        name = "Bankruptcy",
        description = "Last resort for when you're broke",
        permissions = {Permission.MESSAGE_SEND},
        cooldown = 10,
        category = CommandInfo.Category.Economy
)
public class Bankruptcy extends Command {
    private static final int bailout = 5;

    @Override
    public void execute(Context ctx) {
        EconUser econUser = EconomySystem.getUser(ctx.getMember().getId());

        if (econUser.getMoney() > 0) {
            ctx.fail("You can't declare bankruptcy if you have money!");
            return;
        }

        econUser.bailout(bailout);

        ctx.succeed(String.format("You got bailed out for %d!", bailout));
        IO.getSystem(EconIO.class).save();
    }
}
