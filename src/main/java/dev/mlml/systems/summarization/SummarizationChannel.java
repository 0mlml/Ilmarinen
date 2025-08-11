package dev.mlml.systems.summarization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Data
public class SummarizationChannel {
    private String id;
    private long lastSummarizationTime;
    private String lastSummary = "";
    private String wsId = null;
    private WebSocket webSocket = null;

    private static String API_URL = "https://api.zerogpt.com/api/transform/summarize";
    private static String WS_URL = "wss://api.zerogpt.com/api/transform/ws";

    private int currentLength = 0;
    private int MAX_WORD_COUNT = 1000;
    private List<SavedMessage> messages = new ArrayList<>();

    private record SavedMessage(String content, int wordCount) {
    }

    public SummarizationChannel(Channel channel) {
        this.id = channel.getId();
        this.lastSummarizationTime = 0;
        initializeWebSocketConnection();
    }

    private void initializeWebSocketConnection() {
        AtomicReference<CompletableFuture<Void>> connectionFuture = new AtomicReference<>(new CompletableFuture<>());

        HttpClient client = HttpClient.newHttpClient();
        this.webSocket = client.newWebSocketBuilder()
                               .header("Origin", "https://www.zerogpt.com")
                               .header("Cache-Control", "no-cache")
                               .header("Accept-Language", "en-US,en;q=0.9")
                               .header("Pragma", "no-cache")
                               .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36")
                               .buildAsync(URI.create(WS_URL), new WebSocket.Listener() {
                                   @Override
                                   public void onOpen(WebSocket webSocket) {
                                       SummarizationSystem.logger.info("WebSocket connection established");
                                       WebSocket.Listener.super.onOpen(webSocket);
                                   }

                                   @Override
                                   public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                       String message = data.toString();
                                       if (message.startsWith("setClientId,")) {
                                           wsId = message.split(",")[1];
                                           SummarizationSystem.logger.info("Received wsId: " + wsId);
                                           connectionFuture.get().complete(null);
                                       }
                                       return WebSocket.Listener.super.onText(webSocket, data, last);
                                   }

                                   @Override
                                   public void onError(WebSocket webSocket, Throwable error) {
                                       SummarizationSystem.logger.error("WebSocket error: " + error.getMessage());
                                       connectionFuture.get().completeExceptionally(error);
                                       WebSocket.Listener.super.onError(webSocket, error);
                                   }
                               }).join();

        CompletableFuture.runAsync(() -> {
            try {
                connectionFuture.get().get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                SummarizationSystem.logger.error("Timeout waiting for wsId, using fallback");
                wsId = "8c434d8f-ce07-443a-8eee-486f78e72fce";
            }
        });
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

    public String getSummary() {
        if (System.currentTimeMillis() - lastSummarizationTime < 20000) {
            return lastSummary;
        }
        String summary = fetchSummary();
        if (summary.isEmpty()) {
            return "No summary available.";
        }
        lastSummary = summary;
        return summary;
    }

    private String getMessagesString() {
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
            json.put("wsId", wsId);
            return mapper.writeValueAsString(json).replaceAll("\\u001f", "");
        } catch (Exception e) {
            return "{}";
        }
    }

    private String fetchSummary() {
        lastSummarizationTime = System.currentTimeMillis();
        String body = getMessagesString();
        HttpRequest req = HttpRequest.newBuilder()
                                     .uri(URI.create(API_URL))
                                     .header("Accept", "application/json, text/plain, */*")
                                     .header("Accept-Language", "en-US,en;q=0.9")
                                     .header("Content-Type", "application/json")
                                     .header("Origin", "https://www.zerogpt.com")
                                     .header("Referer", "https://www.zerogpt.com/")
                                     .header("Sec-Fetch-Dest", "empty")
                                     .header("Sec-Fetch-Mode", "cors")
                                     .header("Sec-Fetch-Site", "same-site")
                                     .header("User-Agent",
                                             "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
                                     )
                                     .header("sec-ch-ua", "\"Not)A;Brand\";v=\"8\", \"Chromium\";v=\"138\"")
                                     .header("sec-ch-ua-mobile", "?0")
                                     .header("sec-ch-ua-platform", "\"Linux\"")
                                     .method("POST", HttpRequest.BodyPublishers.ofString(body))
                                     .build();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String response = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
            JsonNode jsonNode = new ObjectMapper().readTree(response);

            if (jsonNode.path("data").path("message").asText().contains("no channel exists")) {
                SummarizationSystem.logger.warn("Invalid wsId detected, attempting to renew...");
                return "Waiting for valid connection...";
            }

            String summary = jsonNode.path("data").path("message").asText();
            if (summary.isEmpty()) {
                SummarizationSystem.logger.warn("Received empty summary from API for channel: {}, response: {}",
                                                id,
                                                response
                );
            }
            return summary;
        } catch (Exception e) {
            return "Error summarizing messages: " + e.getMessage();
        }
    }
}
