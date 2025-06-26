package dev.mlml.command.impl;

import dev.mlml.Ilmarinen;
import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.Replies;
import dev.mlml.systems.leveling.LevelingSystem;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;

import java.util.Map;

@CommandInfo(
        keywords = {"levelboard", "lvlboard", "levelleaderboard", "lvllb"},
        name = "Level Leaderboard",
        description = "See the top users by level",
        permissions = {Permission.MESSAGE_SEND},
        category = CommandInfo.Category.Util
)
public class LevelLeaderboard extends Command {

    public LevelLeaderboard() {
        super();
    }

    @Override
    public void execute(Context ctx) {
        Map<String, Integer> topUsers = LevelingSystem.getTopUsers(10);
        
        if (topUsers.isEmpty()) {
            Replies.fail(ctx, "No leveling data available yet!");
            return;
        }

        EmbedBuilder eb = Replies.success(ctx, "Level Leaderboard");
        eb.setDescription("Top users by level across all servers");

        StringBuilder sb = new StringBuilder();
        int rank = 1;
        
        for (Map.Entry<String, Integer> entry : topUsers.entrySet()) {
            String userId = entry.getKey();
            int level = entry.getValue();

            String medal = rank == 1 ? "\uD83E\uDD47" : rank == 2 ? "\uD83E\uDD48" : rank == 3 ? "\uD83E\uDD49" : String.format("%d.", rank);

            try {
                User user = Ilmarinen.getJda().retrieveUserById(userId).complete();
                sb.append(String.format("%s %s - Level %d\n", medal, user.getAsMention(), level));
            } catch (Exception e) {
                sb.append(String.format("%s <@%s> - Level %d\n", medal, userId, level));
            }
            
            rank++;
        }

        eb.addField("Top Players", sb.toString(), false);
        ctx.reply(eb.build());
    }
} 
