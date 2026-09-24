package com.airlineprep.bot.telegram;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TelegramBotClient {

    private final TelegramBotProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public TelegramBotClient(TelegramBotProperties properties, HttpClient httpClient, ObjectMapper mapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    public String getBotUsername() throws InterruptedException {
        JsonNode user = request("getMe", Map.of());
        if (!user.path("is_bot").asBoolean(false) || !user.path("username").isTextual()
                || !user.path("username").textValue().matches("[A-Za-z0-9_]+")) {
            throw new ApiException(0, 0);
        }
        return user.path("username").textValue();
    }

    public JsonNode getUpdates(long offset) throws InterruptedException {
        JsonNode updates = request("getUpdates", Map.of(
                "offset", offset, "timeout", 25, "limit", 20, "allowed_updates", List.of("message", "callback_query")));
        if (!updates.isArray()) {
            throw new ApiException(0, 0);
        }
        return updates;
    }

    public void sendMessage(long chatId, String text) throws InterruptedException {
        sendMessage(chatId, text, Map.of());
    }

    public void answerCallbackQuery(String callbackId) throws InterruptedException {
        JsonNode result = request("answerCallbackQuery", Map.of("callback_query_id", callbackId));
        if (!result.isBoolean() || !result.booleanValue()) throw new ApiException(0, 0);
    }

    public void sendMessage(long chatId, String text, Map<String, ?> markup) throws InterruptedException {
        JsonNode message = request("sendMessage", Map.of("chat_id", chatId, "text", text, "reply_markup", markup));
        if (!message.isObject() || !message.path("message_id").isIntegralNumber()) {
            throw new ApiException(0, 0);
        }
    }

    private JsonNode request(String method, Map<String, ?> body) throws InterruptedException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + properties.token() + "/" + method))
                    .timeout(Duration.ofSeconds(40))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode envelope = mapper.readTree(response.body());
            if (envelope == null || !envelope.isObject()) {
                throw new ApiException(response.statusCode(), 0);
            }
            if (response.statusCode() != 200 || !envelope.path("ok").isBoolean()
                    || !envelope.path("ok").booleanValue()) {
                int code = envelope.path("error_code").asInt(response.statusCode());
                long retryAfter = Math.max(0, envelope.path("parameters").path("retry_after").asLong(0));
                throw new ApiException(code, retryAfter);
            }
            if (!envelope.hasNonNull("result")) {
                throw new ApiException(0, 0);
            }
            return envelope.get("result");
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            // Do not retain raw exceptions: URLs, response bodies, and tokens may be embedded.
            throw new ApiException(0, 0);
        }
    }

    public static final class ApiException extends RuntimeException {
        private final int code;
        private final long retryAfterSeconds;

        ApiException(int code, long retryAfterSeconds) {
            super("Telegram request failed (details withheld)");
            this.code = code;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int code() {
            return code;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
