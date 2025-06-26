package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.Replies;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.command.argument.UserArgument;
import dev.mlml.systems.economy.EconUser;
import dev.mlml.systems.economy.EconomySystem;
import dev.mlml.systems.leveling.LevelingUser;
import dev.mlml.systems.leveling.LevelingSystem;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;

@CommandInfo(
        keywords = {"userinfo", "ui", "level", "lvl", "rank"},
        name = "User Info",
        description = "See your user info and level info",
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
        User user = ctx.getArgument(USER_ARG).map(ParsedArgument::value).orElse(ctx.getAuthor());

        EconUser econUser = EconomySystem.getUser(user.getId());
        LevelingUser levelingUser = LevelingSystem.getUser(user.getId());

        EmbedBuilder eb = Replies.success(ctx, "User Info");
        eb.setThumbnail(user.getAvatarUrl());

        // Combine all accolades
        String[] econAccolades = econUser.getAccolades();
        String[] levelAccolades = levelingUser.getAccolades();
        String accolades = "";
        if (econAccolades.length > 0 || levelAccolades.length > 0) {
            accolades = String.join(" ", econAccolades) + (econAccolades.length > 0 && levelAccolades.length > 0 ? " " : "") + String.join(" ", levelAccolades);
        }

        eb.addField("User", user.getAsMention(), true);
        eb.addField("Accolades", accolades.isEmpty() ? "None" : accolades, true);
        eb.addBlankField(false);

        // Economy stats
        eb.addField("Money", String.format("$%.2f", econUser.getMoney()), true);
        eb.addField("Profit", String.format("$%.2f", econUser.getProfit()), true);
        eb.addField("Loss", String.format("$%.2f", econUser.getLoss()), true);

        eb.addField("Win Rate", String.format("%.2f%%", econUser.getWinRate() * 100), true);
        eb.addField("Games", String.valueOf(econUser.getGames()), true);
        eb.addField("Wins", String.valueOf(econUser.getWins()), true);
        eb.addField("Lost", String.valueOf(econUser.getLost()), true);
        eb.addField("Bankruptcies", String.valueOf(econUser.getBankruptcies()), true);

        eb.addBlankField(false);

        // Leveling stats
        eb.addField("Level", String.valueOf(levelingUser.getLevel()), true);
        eb.addField("Rank", levelingUser.getRank(), true);
        eb.addField("Experience", String.format("%,d XP", levelingUser.getExperience()), true);
        if (levelingUser.getLevel() > 0) {
            long expForNext = levelingUser.getExperienceForNextLevel();
            double progress = levelingUser.getProgressToNextLevel();
            eb.addField("Progress to Level " + (levelingUser.getLevel() + 1),
                    String.format("%.1f%% (%,d XP needed)", progress, expForNext), true);
        } else {
            eb.addField("Progress to Next Level", "-", true);
        }
        eb.addField("Messages Sent", String.format("%,d", levelingUser.getMessages()), true);
        eb.addField("Daily Streak", String.format("%d days", levelingUser.getStreakDays()), true);

        ctx.reply(eb.build());
    }
}
