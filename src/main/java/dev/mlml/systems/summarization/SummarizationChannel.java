package dev.mlml.systems.summarization;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;

import java.util.LinkedList;
import java.util.List;

@Data
public class SummarizationChannel {
    private String id;
    private long lastSummarizationTime;
    private String lastSummary = "";

    private int currentLength = 0;
    private final static int MAX_WORD_COUNT = 750;
    private List<SavedMessage> messages = new LinkedList<>();

    private record SavedMessage(String content, int wordCount) {
    }

    public SummarizationChannel(Channel channel) {
        this.id = channel.getId();
        this.lastSummarizationTime = 0;
    }

    public void addMessage(Message message) {
        int wordCount = message.getContentRaw().split("\\s+").length;
        SavedMessage savedMessage = new SavedMessage(message.getMember()
                                                            .getEffectiveName() + ": " + message.getContentRaw(),
                                                     wordCount
        );
        if (wordCount > MAX_WORD_COUNT) {
            return;
        }
        if (currentLength + wordCount > MAX_WORD_COUNT) {
            while (currentLength + wordCount > MAX_WORD_COUNT && !messages.isEmpty()) {
                SavedMessage oldestMessage = messages.removeFirst();
                currentLength -= oldestMessage.wordCount;
            }
        }
        messages.add(savedMessage);
    }

    public String getCachedSummary() {
        if (System.currentTimeMillis() - lastSummarizationTime < 20000) {
            return lastSummary;
        }
        return null;
    }

    public String getMessagesString() {
        ObjectMapper mapper = new ObjectMapper();
        StringBuilder sb = new StringBuilder();
        sb.append("The following is a discord chatlog:\n");
        for (SavedMessage message : messages) {
            sb.append(message.content).append("\n");
        }
        try {
            var json = mapper.createObjectNode();
            json.put("string", sb.toString());
            json.put("maxWordsPercentage", 0.15);
            json.put("sample", true);
            json.put("earlyStopping", true);
            json.put("numBeams", 5);
            json.put("topK", 50);
            json.put("temperature", 1.5);
            json.put("topP", 1);
            json.put("tone", "standard");
            json.put("style", "text");
            json.put("wsId", ZeroGPTSystem.getWsId());
            return mapper.writeValueAsString(json).replaceAll("\\u001f", "");
        } catch (Exception e) {
            return "{}";
        }
    }
}
