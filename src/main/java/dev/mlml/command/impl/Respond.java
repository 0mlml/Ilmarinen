package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.command.argument.StringArgument;
import dev.mlml.systems.summarization.ZeroGPTSystem;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.util.EnumSet;

@CommandInfo(
        name = "Respond",
        description = "Generate an email reply to the referenced message",
        keywords = {"respond", "r"},
        category = CommandInfo.Category.Fun,
        cooldown = 60
)
public class Respond extends Command {
    private static final StringArgument PURPOSE_ARG = new StringArgument.Builder("purpose").description(
                    "The purpose of the reply, e.g., 'reply to a question', 'respond to feedback'").isVArgs()
            .get();

    public Respond() {
        super(PURPOSE_ARG);
    }

    @Override
    public void execute(Context ctx) {
        String purpose = ctx.getArgument(PURPOSE_ARG)
                            .map(arg -> arg.value().trim())
                            .orElse("Respond to the message in a professional manner");

        if (ctx.getMessage().getReferencedMessage() == null) {
            ctx.fail("You must reply to a message to generate a reply.");
            return;
        }

        if (ctx.getMessage().getReferencedMessage().getAuthor().getId().equals(ctx.getAuthor().getId())) {
            ctx.fail("Do not respond to your own message.");
            return;
        }

        String emailReply = ZeroGPTSystem.fetchEmailReply(ctx, purpose);

        MessageCreateBuilder emailReplyBuilder = new MessageCreateBuilder().setContent(emailReply)
                                                                           .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class));

        ctx.getMessage().getReferencedMessage().reply(emailReplyBuilder.build()).queue(success -> {
            ctx.getMessage().delete().queue();
        });
    }
}
