package com.airlineprep.bot.telegram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.concurrent.Semaphore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** No volatile queue: acknowledge only after handling; domain transactions fence retries. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "${telegram.bot.enabled:false} and '${telegram.bot.mode:POLLING}'.equalsIgnoreCase('WEBHOOK')")
public class TelegramWebhookController {
    public static final String PATH = "/api/telegram/webhook";
    public static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";
    private static final int MAX_BYTES = 256 * 1024;
    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private final byte[] secret;
    private final TelegramBotClient client;
    private final TelegramUpdateHandler handler;
    private final ObjectMapper mapper;
    private final Semaphore capacity = new Semaphore(2);
    private volatile String username;

    public TelegramWebhookController(TelegramBotProperties properties, TelegramBotClient client,
            TelegramUpdateHandler handler, ObjectMapper mapper) {
        if (!properties.enabled() || properties.mode() != TelegramBotProperties.Mode.WEBHOOK
                || !properties.isWebhookSecretConfigured()) {
            throw new IllegalStateException("Webhook requires enabled WEBHOOK mode and a configured secret");
        }
        secret = properties.webhookSecret().getBytes(StandardCharsets.UTF_8);
        this.client = client;
        this.handler = handler;
        this.mapper = mapper;
        log.info("Telegram WEBHOOK mode enabled; long polling disabled");
    }

    @PostMapping(PATH)
    public ResponseEntity<Void> receive(HttpServletRequest request) {
        var headers = Collections.list(request.getHeaders(SECRET_HEADER));
        if (headers.size() != 1 || headers.getFirst().isBlank() || headers.getFirst().length() > 256
                || !MessageDigest.isEqual(secret, headers.getFirst().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(403).build();
        }
        if (!capacity.tryAcquire()) return unavailable();
        try {
            if (request.getContentLengthLong() > MAX_BYTES) return ResponseEntity.status(413).build();
            JsonNode update;
            try {
                byte[] bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) return ResponseEntity.status(413).build();
                update = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);
            } catch (IOException exception) {
                return ResponseEntity.badRequest().build();
            }
            if (update == null || !update.isObject()) return ResponseEntity.badRequest().build();
            var id = update.path("update_id");
            if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() < 0
                    || id.longValue() == Long.MAX_VALUE) return ResponseEntity.badRequest().build();
            // Plain /start and callbacks need no identity request. Resolve mentions only once.
            if (username == null && update.path("message").path("text").asText("").startsWith("/start@")) {
                username = client.getBotUsername();
            }
            handler.handleWebhook(update, username);
            return ResponseEntity.ok().build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unavailable();
        } catch (RuntimeException exception) {
            log.warn("Telegram webhook processing unavailable; delivery may retry; details withheld");
            return unavailable();
        } finally {
            capacity.release();
        }
    }

    private ResponseEntity<Void> unavailable() {
        return ResponseEntity.status(503).header("Retry-After", "5").build();
    }
}
