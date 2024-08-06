package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.Replies;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.command.argument.UserArgument;
import dev.mlml.economy.EconUser;
import dev.mlml.economy.Economy;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;

@CommandInfo(
        keywords = {"userinfo", "ui"},
        name = "User Info",
        description = "See your user info",
        permissions = {Permission.MESSAGE_SEND},
        category = CommandInfo.Category.Economy
)
public class UserInfo extends Command {
    private static final UserArgument USER_ARG = new UserArgument.Builder("user")
            .description("The user to get info on")
            .get();

    public UserInfo() {
        super(USER_ARG);
    }

    @Override
    public void execute(Context ctx) {
        User user = ctx.getArgument(USER_ARG).map(ParsedArgument::getValue).orElse(ctx.getAuthor());

        EconUser econUser = Economy.getUser(user.getId());

        EmbedBuilder eb = Replies.success(ctx, "User Info");
        eb.addField("User", user.getAsMention() + " " + String.join(", ", econUser.getAccolades()), false);
        eb.addField("Money", String.format("$%.2f", econUser.getMoney()), true);
        eb.addField("Profit", String.format("$%.2f", econUser.getProfit()), true);
        eb.addField("Loss", String.format("$%.2f", econUser.getLoss()), true);
        eb.addField("Win Rate", String.format("%.2f%%", econUser.getWinRate() * 100), true);
        eb.addField("Games", String.valueOf(econUser.getGames()), true);
        eb.addField("Wins", String.valueOf(econUser.getWins()), true);
        eb.addField("Lost", String.valueOf(econUser.getLost()), true);
        eb.addField("Bankruptcies", String.valueOf(econUser.getBankruptcies()), true);

        ctx.reply(eb.build());
    }
}
