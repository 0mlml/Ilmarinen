package dev.mlml.command.impl;

import dev.mlml.command.Command;
import dev.mlml.command.CommandInfo;
import dev.mlml.command.Context;
import dev.mlml.systems.summarization.ZeroGPTSystem;

@CommandInfo(
        keywords = {"summary", "sum", "tldr"},
        name = "Summary",
        description = "Get a summary of the last 50 messages in the channel",
        cooldown = 60
)
public class Summary extends Command {
    public Summary() {
        super();
    }

    @Override
    public void execute(Context ctx) {
        if (ctx.getChannel() == null) {
            ctx.fail("This command can only be used in a text channel.");
            return;
        }

        String summary = ZeroGPTSystem.getSummary(ctx.getChannel());
        if (summary.isEmpty()) {
            ctx.reply("No messages to summarize in this channel.");
        } else {
            ctx.reply(summary);
        }
    }
}
