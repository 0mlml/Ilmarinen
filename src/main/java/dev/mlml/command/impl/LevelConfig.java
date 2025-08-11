package dev.mlml.command.impl;

import dev.mlml.command.CommandInfo;
import dev.mlml.command.SubcommandCommand;
import dev.mlml.command.SubcommandContext;
import dev.mlml.command.argument.ChannelArgument;
import dev.mlml.command.argument.IntegerArgument;
import dev.mlml.command.argument.SubcommandArgument;
import dev.mlml.systems.IO;
import dev.mlml.systems.leveling.LevelingGuild;
import dev.mlml.systems.leveling.LevelingIO;
import dev.mlml.systems.leveling.LevelingSystem;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

@CommandInfo(
        keywords = {"levelconfig", "lvlconfig", "levelsettings"},
        name = "Level Configuration",
        description = "Configure leveling settings for this server",
        permissions = {Permission.ADMINISTRATOR},
        category = CommandInfo.Category.Moderation
)
public class LevelConfig extends SubcommandCommand {

    private static final ChannelArgument CHANNEL_ARG = new ChannelArgument.Builder("channel")
            .description("Channel for level-up announcements")
            .require()
            .get();

    private static final IntegerArgument COOLDOWN_ARG = new IntegerArgument.Builder("seconds")
            .description("Message cooldown in seconds")
            .require()
            .min(1)
            .max(3600)
            .get();

    private static final IntegerArgument EXP_ARG = new IntegerArgument.Builder("amount")
            .description("Base experience per message")
            .require()
            .min(1)
            .max(1000)
            .get();

    public LevelConfig() {
        super(SubcommandArgument.builder("action")
                      .description("Configuration action to perform")
                      .require()
                      .addSubcommand("enable")
                      .addSubcommand("disable")
                      .addSubcommand("channel", CHANNEL_ARG)
                      .addSubcommand("cooldown", COOLDOWN_ARG)
                      .addSubcommand("exp", EXP_ARG)
                      .addSubcommand("show")
                      .get());
    }

    @Override
    public void executeSubcommand(SubcommandContext subCtx) {
        switch (subCtx.subcommand()) {
            case "enable" -> handleEnable(subCtx);
            case "disable" -> handleDisable(subCtx);
            case "channel" -> handleChannel(subCtx);
            case "cooldown" -> handleCooldown(subCtx);
            case "exp" -> handleExp(subCtx);
            case "show" -> handleShow(subCtx);
            default -> subCtx.reply("Unknown subcommand: " + subCtx.subcommand());
        }
    }

    private void handleEnable(SubcommandContext subCtx) {
        LevelingGuild guild = LevelingSystem.getGuild(subCtx.baseContext().getGuild().getId());
        guild.setLevelingEnabled(true);
        IO.getSystem(LevelingIO.class).save();
        subCtx.reply("Leveling is now enabled for this server.");
    }

    private void handleDisable(SubcommandContext subCtx) {
        LevelingGuild guild = LevelingSystem.getGuild(subCtx.baseContext().getGuild().getId());
        guild.setLevelingEnabled(false);
        IO.getSystem(LevelingIO.class).save();
        subCtx.reply("Leveling is now disabled for this server.");
    }

    private void handleChannel(SubcommandContext subCtx) {
        var channelArg = subCtx.getSubcommandArgument(CHANNEL_ARG);
        if (channelArg == null) {
            subCtx.reply("Please specify a channel.");
            return;
        }

        TextChannel channel = (TextChannel) channelArg.value();
        LevelingGuild guild = LevelingSystem.getGuild(subCtx.baseContext().getGuild().getId());
        guild.setLevelUpChannel(channel.getId());
        IO.getSystem(LevelingIO.class).save();
        subCtx.reply("Level-up announcements will be sent to " + channel.getAsMention());
    }

    private void handleCooldown(SubcommandContext subCtx) {
        var cooldownArg = subCtx.getSubcommandArgument(COOLDOWN_ARG);
        if (cooldownArg == null) {
            subCtx.reply("Please specify cooldown in seconds.");
            return;
        }

        int cooldownSeconds = cooldownArg.value();
        LevelingGuild guild = LevelingSystem.getGuild(subCtx.baseContext().getGuild().getId());
        guild.setMessageCooldownMs(cooldownSeconds * 1000L);
        IO.getSystem(LevelingIO.class).save();
        subCtx.reply("Message cooldown set to " + cooldownSeconds + " seconds.");
    }

    private void handleExp(SubcommandContext subCtx) {
        var expArg = subCtx.getSubcommandArgument(EXP_ARG);
        if (expArg == null) {
            subCtx.reply("Please specify experience amount.");
            return;
        }

        int expAmount = expArg.value();
        LevelingGuild guild = LevelingSystem.getGuild(subCtx.baseContext().getGuild().getId());
        guild.setBaseExperiencePerMessage(expAmount);
        IO.getSystem(LevelingIO.class).save();
        subCtx.reply("Base experience set to " + expAmount + " XP per message.");
    }

    private void handleShow(SubcommandContext subCtx) {
        LevelingGuild guild = LevelingSystem.getGuild(subCtx.baseContext().getGuild().getId());
        
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Level Configuration")
                .setColor(java.awt.Color.GREEN);

        eb.addField("Enabled", guild.isLevelingEnabled() ? "Yes" : "No", true);
        eb.addField("Channel", guild.getLevelUpChannelId() != null ? 
                   "<#" + guild.getLevelUpChannelId() + ">" : "Not set", true);
        eb.addField("Cooldown", guild.getMessageCooldownMs() / 1000 + " seconds", true);
        eb.addField("Base XP", guild.getBaseExperiencePerMessage() + " XP", true);

        subCtx.baseContext().getMessage().replyEmbeds(eb.build()).queue();
    }
} 