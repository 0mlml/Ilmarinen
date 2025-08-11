package dev.mlml.systems.summarization;

import dev.mlml.systems.starboard.StarboardSystem;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class SummarizationSystem {
    protected static final Logger logger = LoggerFactory.getLogger(SummarizationSystem.class);

    private static final HashMap<Channel, SummarizationChannel> channels = new HashMap<>();

    public static void handleMessage(Message message) {
        if (message.getContentRaw().isEmpty() || message.getAuthor().isBot()) {
            return;
        }

        Channel channel = message.getChannel();
        SummarizationChannel summarizationChannel = channels.computeIfAbsent(channel, SummarizationChannel::new);
        summarizationChannel.addMessage(message);
    }

    public static String getSummary(Channel channel) {
        if (!channels.containsKey(channel)) {
            return "No summary available for this channel.";
        }
        SummarizationChannel summarizationChannel = channels.get(channel);
        return summarizationChannel.getSummary();
    }
}
