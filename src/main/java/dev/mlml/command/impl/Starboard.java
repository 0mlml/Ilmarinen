package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.argument.*;
import dev.mlml.systems.starboard.StarboardGuild;
import dev.mlml.systems.starboard.StarboardSystem;
import net.dv8tion.jda.api.Permission;

@CommandInfo(
        name = "starboard",
        keywords = {"starboard", "star"},
        description = "Manage starboard settings",
        permissions = {Permission.MANAGE_SERVER},
        category = CommandInfo.Category.Moderation
)
public class Starboard extends Command {

    private static final OptionArgument ACTION_ARG = new OptionArgument.Builder("action").addOption("set")
                                                                                         .addOption("get")
            .description("The action to perform")
            .require()
            .get();

    private static final OptionArgument SETTING_ARG = new OptionArgument.Builder("setting").addOption("channel")
                                                                                           .addOption("threshold")
                                                                                           .addOption("emoji")
            .description("The setting to configure (optional for 'get' to show all)")
            .get();

    private static final StringArgument VALUE_ARG = new StringArgument.Builder("value").description(
                    "The value to set (required for 'set' action)")
            .get();

    public Starboard() {
        super(ACTION_ARG, SETTING_ARG, VALUE_ARG);
    }

    @Override
    public void execute(Context ctx) {
        String action = ctx.getArgument(ACTION_ARG).map(ParsedArgument::getValue).orElse("");
        String setting = ctx.getArgument(SETTING_ARG).map(ParsedArgument::getValue).orElse("");
        String value = ctx.getArgument(VALUE_ARG).map(ParsedArgument::getValue).orElse("");

        switch (action.toLowerCase()) {
            case "set" -> handleSet(ctx, setting, value);
            case "get" -> handleGet(ctx, setting);
            default -> ctx.fail("Invalid action. Use 'set' or 'get'");
        }
    }

    private void handleSet(Context ctx, String setting, String value) {
        if (setting.isEmpty()) {
            ctx.fail("Please specify a setting to configure: channel, threshold, emoji");
            return;
        }

        if (value.isEmpty()) {
            ctx.fail("Please provide a value to set");
            return;
        }

        switch (setting.toLowerCase()) {
            case "channel" -> {
                String channelId = parseChannelId(value);
                if (channelId == null) {
                    ctx.fail("Invalid channel format. Use #channel or channel ID");
                    return;
                }

                StarboardSystem.setChannel(ctx.getGuild().getId(), channelId);
                ctx.succeed("Starboard channel set to <#" + channelId + ">");
            }
            case "threshold" -> {
                try {
                    int threshold = Integer.parseInt(value);
                    if (threshold < 1) {
                        ctx.fail("Threshold must be at least 1");
                        return;
                    }

                    StarboardSystem.setThreshold(ctx.getGuild().getId(), threshold);
                    ctx.succeed("Starboard threshold set to " + threshold);
                } catch (NumberFormatException e) {
                    ctx.fail("Invalid number format for threshold");
                }
            }
            case "emoji" -> {
                // TODO: Validate emoji
                if (value.length() > 10) {
                    ctx.fail("Emoji too long");
                    return;
                }

                StarboardSystem.setEmoji(ctx.getGuild().getId(), value);
                ctx.succeed("Starboard emoji set to " + value);
            }
            default -> ctx.fail("Unknown setting: " + setting + ". Use: channel, threshold, emoji");
        }
    }

    private void handleGet(Context ctx, String setting) {
        if (setting.isEmpty()) {
            showAllSettings(ctx);
        } else {
            showSpecificSetting(ctx, setting);
        }
    }

    private void showAllSettings(Context ctx) {
        StarboardGuild config = StarboardSystem.getGuild(ctx.getGuild().getId());

        StringBuilder sb = new StringBuilder("**Starboard Settings:**\n");
        sb.append("Channel: ")
          .append(config.getChannel() != null ? "<#" + config.getChannel().getId() + ">" : "Not set")
          .append("\n");
        sb.append("Threshold: ").append(config.getThreshold()).append("\n");
        sb.append("Emoji: ").append(config.getEmoji() != null ? config.getEmoji() : "Not set").append("\n");

        ctx.inform(sb.toString());
    }

    private void showSpecificSetting(Context ctx, String setting) {
        StarboardGuild config = StarboardSystem.getGuild(ctx.getGuild().getId());

        switch (setting.toLowerCase()) {
            case "channel" -> ctx.inform("Starboard channel: " + (config.getChannel() != null
                                                                  ? "<#" + config.getChannel() + ">"
                                                                  : "Not set"));
            case "threshold" -> ctx.inform("Starboard threshold: " + config.getThreshold());
            case "emoji" ->
                    ctx.inform("Starboard emoji: " + (config.getEmoji() != null ? config.getEmoji() : "Not set"));
            default -> ctx.fail("Unknown setting: " + setting + ". Use: channel, threshold, emoji");
        }
    }

    private String parseChannelId(String input) {
        if (input.startsWith("<#") && input.endsWith(">")) {
            return input.substring(2, input.length() - 1);
        }

        if (input.matches("\\d{17,19}")) {
            return input;
        }

        return null;
    }
}