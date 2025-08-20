package dev.mlml.systems.summarization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mlml.command.Context;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ZeroGPTSystem {
    protected static final Logger logger = LoggerFactory.getLogger(ZeroGPTSystem.class);

    private static final HashMap<Channel, SummarizationChannel> channels = new HashMap<>();
    @Getter
    private static String wsId = null;
    private static WebSocket webSocket = null;

    private final static String SUMMARY_API_URL = "https://api.zerogpt.com/api/transform/summarize";
    private final static String EMAIL_API_URL = "https://api.zerogpt.com/api/transform/generateEmail";
    private final static String WS_URL = "wss://api.zerogpt.com/api/transform/ws";

    private static void initializeWebSocketConnection() {
        if (webSocket != null && (webSocket.isInputClosed() || webSocket.isOutputClosed())) {
            return;
        }
        AtomicReference<CompletableFuture<Void>> connectionFuture = new AtomicReference<>(new CompletableFuture<>());

        HttpClient client = HttpClient.newHttpClient();
        webSocket = client.newWebSocketBuilder()
                          .header("Origin", "https://www.zerogpt.com")
                          .header("Cache-Control", "no-cache")
                          .header("Accept-Language", "en-US,en;q=0.9")
                          .header("Pragma", "no-cache")
                          .header("User-Agent",
                                  "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
                          )
                          .buildAsync(URI.create(WS_URL), new WebSocket.Listener() {
                                          @Override
                                          public void onOpen(WebSocket webSocket) {
                                              ZeroGPTSystem.logger.info("WebSocket connection established");
                                              WebSocket.Listener.super.onOpen(webSocket);
                                          }

                                          @Override
                                          public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                              String message = data.toString();
                                              if (message.startsWith("setClientId,")) {
                                                  wsId = message.split(",")[1];
                                                  ZeroGPTSystem.logger.info("Received wsId: " + wsId);
                                                  connectionFuture.get().complete(null);
                                              }
                                              return WebSocket.Listener.super.onText(webSocket, data, last);
                                          }

                                          @Override
                                          public void onError(WebSocket webSocket, Throwable error) {
                                              ZeroGPTSystem.logger.error("WebSocket error: " + error.getMessage());
                                              connectionFuture.get().completeExceptionally(error);
                                              WebSocket.Listener.super.onError(webSocket, error);
                                          }
                                      }
                          )
                          .join();

        CompletableFuture.runAsync(() -> {
            try {
                connectionFuture.get()
                        .get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                ZeroGPTSystem.logger.error("Timeout waiting for wsId");
                wsId = null;
            }
        });
    }

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
        if (summarizationChannel.getCachedSummary() != null) {
            return summarizationChannel.getCachedSummary();
        }
        String summary = fetchSummary(summarizationChannel);
        if (summary.isEmpty()) {
            return "Error summarizing messages, please try again later.";
        } else {
            summarizationChannel.getMessages().clear();
            return summary;
        }
    }

    private static String fetchSummary(SummarizationChannel channel) {
        initializeWebSocketConnection();
        String body = channel.getMessagesString();
        HttpRequest req = HttpRequest.newBuilder()
                                     .uri(URI.create(SUMMARY_API_URL))
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
                ZeroGPTSystem.logger.warn("Invalid wsId detected, attempting to renew...");
                wsId = null;
                return "Websocket connection lost, please try again.";
            }

            String summary = jsonNode.path("data").path("message").asText();
            if (summary.isEmpty()) {
                ZeroGPTSystem.logger.warn("Received empty summary from API for channel: {}, response: {}",
                                          channel.getId(),
                                          response
                );
            }
            channel.setLastSummary(summary);
            channel.setLastSummarizationTime(System.currentTimeMillis());
            return summary;
        } catch (Exception e) {
            return "Error summarizing messages: " + e.getMessage();
        }
    }

    private static String createEmailReplyPayload(String receivedEmail, String subjectLine, String sender, String recipient, String purpose) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            HashMap<String, Object> payload = new HashMap<>();
            payload.put("string", "");
            payload.put("maxWordsPercentage", 0.15);
            payload.put("sample", true);
            payload.put("earlyStopping", true);
            payload.put("numBeams", 5);
            payload.put("topK", 50);
            payload.put("temperature", 1.5);
            payload.put("topP", 1);
            payload.put("wsId", wsId);
            payload.put("tone", "standard");
            payload.put("style", "text");
            payload.put("emailType", "reply");
            payload.put("emailLanguage", "english");
            payload.put("purpose", purpose);
            payload.put("length", "65");
            payload.put("receivedEmail", receivedEmail);
            payload.put("subjectLine", subjectLine);
            payload.put("sender", sender);
            payload.put("recipient", recipient);
            return mapper.writeValueAsString(payload);
        } catch (Exception e) {
            logger.error("Error creating email reply payload: {}", e.getMessage());
            return "{}";
        }
    }

    public static String fetchEmailReply(Context ctx, String purpose) {
        initializeWebSocketConnection();
        Message targetMessage = ctx.getMessage().getReferencedMessage();
        if (targetMessage == null) {
            return "No message to reply to.";
        }
        if (targetMessage.getContentRaw().isEmpty()) {
            return "Cannot reply to an empty message.";
        }

        String recievedContent = targetMessage.getContentRaw();
        String subjectLine = purpose;
        String sender = ctx.getMessage().getMember().getEffectiveName();
        String recipient = targetMessage.getMember().getEffectiveName();

        String body = createEmailReplyPayload(recievedContent, subjectLine, sender, recipient, purpose);
        HttpRequest req = HttpRequest.newBuilder()
                                     .uri(URI.create(EMAIL_API_URL))
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
                ZeroGPTSystem.logger.warn("Invalid wsId detected, attempting to renew...");
                wsId = null;
                return "Websocket connection lost, please try again.";
            }

            String reply = jsonNode.path("data").path("message").asText();
            if (reply.isEmpty()) {
                ZeroGPTSystem.logger.warn("Received empty reply from API for context: {}, response: {}",
                                          ctx.getMessage().getId(),
                                          response
                );
            }
            return reply;
        } catch (Exception e) {
            return "Error generating email reply: " + e.getMessage();
        }
    }
}
