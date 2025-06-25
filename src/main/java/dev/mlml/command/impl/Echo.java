package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.argument.ChannelArgument;
import dev.mlml.command.argument.ParsedArgument;
import dev.mlml.command.argument.StringArgument;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.EnumSet;
import java.util.Optional;

@CommandInfo(
        keywords = {"echo", "say"},
        name = "Echo",
        description = "Echoes the input",
        category = CommandInfo.Category.Util
)
public class Echo extends Command {
    private static final StringArgument TEXT_ARG = new StringArgument.Builder("text")
            .description("The text to echo")
            .isVArgs()
            .require()
            .get();
    private static final ChannelArgument CHANNEL_ARG = new ChannelArgument.Builder("channel")
            .description("The channel to echo in")
            .get();

    public Echo() {
        super(CHANNEL_ARG, TEXT_ARG);
    }

    @Override
    public void execute(Context ctx) {
        Optional<ParsedArgument<Channel>> channelArg = ctx.getArgument(CHANNEL_ARG);
        String message = ctx.getArgument(TEXT_ARG).map(ParsedArgument::value).orElse("");

        if (channelArg.isPresent()) {
            Channel channel = channelArg.get().value();
            if (channel.getType() != ChannelType.TEXT) {
                ctx.fail("Invalid channel type");
                return;
            }
            if (!ctx.getMember().hasPermission((TextChannel) channel, Permission.MESSAGE_SEND)) {
                ctx.fail("You don't have permission to send messages in that channel");
                return;
            }
            ((TextChannel) channel).sendMessage(message)
                                   .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                                   .queue();
        } else {
            ctx.getMessage().reply(message).setAllowedMentions(EnumSet.noneOf(Message.MentionType.class)).queue();
        }
    }
}
