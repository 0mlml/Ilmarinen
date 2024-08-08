package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.Replies;
import dev.mlml.command.argument.OptionArgument;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.economy.EconUser;
import dev.mlml.economy.Economy;
import net.dv8tion.jda.api.EmbedBuilder;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@CommandInfo(
        keywords = {"leaderboard", "top", "lb"},
        name = "Leaderboard",
        description = "Shows the leaderboard",
        category = CommandInfo.Category.Economy
)
public class Leaderboard extends Command {
    private final static OptionArgument SORT_BY = new OptionArgument.Builder("sort")
            .addOptions(Arrays.stream(SortBy.values()).map(SortBy::toString).toList())
            .get();

    public Leaderboard() {
        super(SORT_BY);
    }

    @Override
    public void execute(Context ctx) {
        SortBy sortBy = SortBy.fromString(ctx.getArgument(SORT_BY).map(ParsedArgument::getValue).orElse("money"));

        Collection<EconUser> users = Economy.getUsers().values();
        List<String> sortedUsers = switch (sortBy) {
            case MONEY -> users.stream()
                               .sorted((a, b) -> (int) (b.getMoney() - a.getMoney()))
                               .map(u -> String.format("<@%s> - $%.2f\n", u.getId(), u.getMoney()))
                               .toList();
            case PROFIT -> users.stream()
                                .sorted((a, b) -> (int) (b.getProfit() - a.getProfit()))
                                .map(u -> String.format("<@%s> - $%.2f\n", u.getId(), u.getProfit()))
                                .toList();
            case LOSS -> users.stream()
                              .sorted((a, b) -> (int) (b.getLoss() - a.getLoss()))
                              .map(u -> String.format("<@%s> - $%.2f\n", u.getId(), u.getLoss()))
                              .toList();
            case WINRATE -> users.stream()
                                 .sorted((a, b) -> (int) (b.getWinRate() - a.getWinRate()))
                                 .map(u -> String.format("<@%s> - %.2f%%\n", u.getId(), u.getWinRate() * 100))
                                 .toList();
            case GAMES -> users.stream()
                               .sorted((a, b) -> b.getGames() - a.getGames())
                               .map(u -> String.format("<@%s> - %d games\n", u.getId(), u.getGames()))
                               .toList();
            case WINS -> users.stream()
                              .sorted((a, b) -> b.getWins() - a.getWins())
                              .map(u -> String.format("<@%s> - %d wins\n", u.getId(), u.getWins()))
                              .toList();
            case LOST -> users.stream()
                              .sorted((a, b) -> b.getLost() - a.getLost())
                              .map(u -> String.format("<@%s> - %d losses\n", u.getId(), u.getLost()))
                              .toList();
            case BANKRUPTCIES -> users.stream()
                                      .sorted((a, b) -> b.getBankruptcies() - a.getBankruptcies())
                                      .map(u -> String.format("<@%s> - %d bankruptcies\n",
                                                              u.getId(),
                                                              u.getBankruptcies()
                                      ))
                                      .toList();
        };


        EmbedBuilder resEmbed = Replies.base(ctx);
        resEmbed.setTitle("Leaderboard");
        resEmbed.setDescription("Showing the leaderboard sorted by " + sortBy + "\n");
        resEmbed.setColor(0x00ff00);

        for (int i = 0; i < Math.min(10, sortedUsers.size()); i++) {
            resEmbed.appendDescription(String.format("%d. %s", i + 1, sortedUsers.get(i)));
        }

        ctx.reply(resEmbed.build());
    }

    public enum SortBy {
        MONEY,
        PROFIT,
        LOSS,
        WINRATE,
        GAMES,
        WINS,
        LOST,
        BANKRUPTCIES;

        public String toString() {
            return name().toLowerCase();
        }

        public static SortBy fromString(String str) {
            return valueOf(str.toUpperCase());
        }
    }
}
