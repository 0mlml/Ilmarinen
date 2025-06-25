package dev.mlml.command.impl;

import dev.mlml.Utils;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.SubcommandCommand;
import dev.mlml.command.SubcommandContext;
import dev.mlml.command.argument.ChannelArgument;
import dev.mlml.command.argument.IntegerArgument;
import dev.mlml.command.argument.StringArgument;
import dev.mlml.command.argument.SubcommandArgument;
import dev.mlml.systems.giveaway.GiveawayGuild;
import dev.mlml.systems.giveaway.GiveawaySystem;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.time.Duration;
import java.time.LocalDateTime;

@CommandInfo(
        keywords = {"giveaway", "ga", "gastart", "gaend"},
        name = "Giveaway",
        description = "Manage giveaways in your server",
        category = CommandInfo.Category.Util,
        permissions = {Permission.MANAGE_EVENTS},
        cooldown = 5
)
public class Giveaway extends SubcommandCommand {

    private static final ChannelArgument CHANNEL_ARG = new ChannelArgument.Builder("channel").description(
                    "Channel to host the giveaway in")
            .require()
            .get();

    private static final StringArgument DURATION_ARG = new StringArgument.Builder("duration").description(
                    "Duration (e.g., 1h, 30m, 2d)")
            .require()
            .get();

    private static final IntegerArgument WINNERS_ARG = new IntegerArgument.Builder("winners").description(
                    "Number of winners")
            .require().min(1).max(20)
            .get();

    private static final StringArgument PRIZE_ARG = new StringArgument.Builder("prize").description("Giveaway prize")
            .require().isVArgs()
            .get();

    private static final StringArgument GIVEAWAY_ID_ARG = new StringArgument.Builder("giveaway-id").description(
                    "Message ID of the giveaway")
            .require()
            .get();

    public Giveaway() {
        super(SubcommandArgument.builder("action")
                      .description("Giveaway action to perform")
                      .require()
                      .addSubcommand("start", CHANNEL_ARG, DURATION_ARG, WINNERS_ARG, PRIZE_ARG)
                      .addSubcommand("end", GIVEAWAY_ID_ARG)
                      .addSubcommand("reroll", GIVEAWAY_ID_ARG)
                      .addSubcommand("list")
                      .addSubcommand("cancel", GIVEAWAY_ID_ARG)
                      .get());
    }

    @Override
    public void executeSubcommand(SubcommandContext subCtx) {
        switch (subCtx.subcommand()) {
            case "start" -> handleStart(subCtx);
            case "end" -> handleEnd(subCtx);
            case "reroll" -> handleReroll(subCtx);
            case "list" -> handleList(subCtx);
            case "cancel" -> handleCancel(subCtx);
            default -> subCtx.reply("Unknown subcommand: " + subCtx.subcommand());
        }
    }

    private void handleStart(SubcommandContext subCtx) {
        var channelArg = subCtx.getSubcommandArgument(CHANNEL_ARG);
        var durationArg = subCtx.getSubcommandArgument(DURATION_ARG);
        var winnersArg = subCtx.getSubcommandArgument(WINNERS_ARG);
        var prizeArg = subCtx.getSubcommandArgument(PRIZE_ARG);

        if (channelArg == null || durationArg == null || winnersArg == null || prizeArg == null) {
            subCtx.reply("Missing required arguments for start command");
            return;
        }

        TextChannel giveawayChannel = (TextChannel) channelArg.value();
        String durationStr = durationArg.value();
        int winners = winnersArg.value();
        String prize = prizeArg.value();

        if (!giveawayChannel.canTalk(subCtx.baseContext().getMember())) {
            subCtx.reply("You don't have permission to send messages in " + giveawayChannel.getAsMention());
            return;
        }

        Duration duration;
        try {
            duration = Utils.parseDuration(durationStr);
        } catch (IllegalArgumentException e) {
            subCtx.reply("Invalid duration format: " + durationStr + "\nUse format like: 1h, 30m, 2d, 1h30m");
            return;
        }

        if (duration.toMillis() > Integer.MAX_VALUE) {
            subCtx.reply("Duration is too long! Maximum is about 24 days.");
            return;
        }

        if (duration.toSeconds() < 1) {
            subCtx.reply("Duration must be at least 1 second!");
            return;
        }

        LocalDateTime endTime = LocalDateTime.now().plus(duration);

        subCtx.reply("Setting up giveaway...");

        GiveawaySystem.startGiveaway(subCtx.baseContext().getGuild().getId(),
                                     giveawayChannel.getId(),
                                     subCtx.baseContext().getMember().getId(),
                                     prize,
                                     winners,
                                     endTime,
                                     (giveawayId) -> {
                                         subCtx.baseContext()
                                               .getChannel()
                                               .sendMessage("Giveaway started! Message ID: " + giveawayId)
                                               .queue();
                                     },
                                     (error) -> {
                                         subCtx.reply("Failed to start giveaway: " + error);
                                     }
        );
    }

    private void handleEnd(SubcommandContext subCtx) {
        var giveawayIdArg = subCtx.getSubcommandArgument(GIVEAWAY_ID_ARG);
        if (giveawayIdArg == null) {
            subCtx.reply("Please provide a giveaway message ID");
            return;
        }

        String giveawayId = giveawayIdArg.value();
        String guildId = subCtx.baseContext().getGuild().getId();

        GiveawaySystem.endGiveaway(
                giveawayId,
                                   () -> subCtx.baseContext().getMessage().reply("Giveaway ended successfully!"),
                                   (error) -> subCtx.reply("Failed to end giveaway: " + error)
        );
    }

    private void handleReroll(SubcommandContext subCtx) {
        var giveawayIdArg = subCtx.getSubcommandArgument(GIVEAWAY_ID_ARG);
        if (giveawayIdArg == null) {
            subCtx.reply("Please provide a giveaway message ID");
            return;
        }

        String giveawayId = giveawayIdArg.value();
        String guildId = subCtx.baseContext().getGuild().getId();

        GiveawaySystem.rerollGiveaway(
                giveawayId,
                                      () -> subCtx.baseContext().getMessage().reply("Giveaway rerolled successfully!"),
                                      (error) -> subCtx.reply("Failed to reroll giveaway: " + error)
        );
    }

    private void handleList(SubcommandContext subCtx) {
        String guildId = subCtx.baseContext().getGuild().getId();
        GiveawayGuild guild = GiveawaySystem.getGuild(guildId);

        var activeGiveaways = guild.getActiveGiveaways();
        if (activeGiveaways.isEmpty()) {
            subCtx.reply("No active giveaways in this server.");
            return;
        }

        StringBuilder sb = new StringBuilder("**Active Giveaways:**\n\n");
        for (var giveaway : activeGiveaways.values()) {
            sb.append("**").append(giveaway.getPrize()).append("**\n");
            sb.append("ID: `").append(giveaway.getMessageId()).append("`\n");
            sb.append("Channel: <#").append(giveaway.getChannelId()).append(">\n");
            sb.append("Winners: ").append(giveaway.getWinners()).append("\n");
            sb.append("Ends: <t:")
              .append(giveaway.getEndTime().toEpochSecond(java.time.ZoneOffset.UTC))
              .append(":R>\n\n");
        }

        subCtx.reply(sb.toString());
    }

    private void handleCancel(SubcommandContext subCtx) {
        var giveawayIdArg = subCtx.getSubcommandArgument(GIVEAWAY_ID_ARG);
        if (giveawayIdArg == null) {
            subCtx.reply("Please provide a giveaway message ID");
            return;
        }

        String giveawayId = giveawayIdArg.value();
        String guildId = subCtx.baseContext().getGuild().getId();

        GiveawaySystem.cancelGiveaway(guildId,
                                      giveawayId,
                                      () -> subCtx.reply("Giveaway cancelled successfully!"),
                                      (error) -> subCtx.reply("Failed to cancel giveaway: " + error)
        );
    }
}