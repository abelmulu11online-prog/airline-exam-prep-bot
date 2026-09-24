package com.airlineprep.bot.telegram;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;

public class TelegramUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateHandler.class);

    private final TelegramBotClient client;
    private final MessageSource messages;

    public TelegramUpdateHandler(TelegramBotClient client, MessageSource messages) {
        this.client = client;
        this.messages = messages;
    }

    public void handle(JsonNode update, String botUsername) throws InterruptedException {
        if (update == null || !update.isObject()) {
            return;
        }
        JsonNode message = update.path("message");
        JsonNode text = message.path("text");
        JsonNode chatId = message.path("chat").path("id");
        if (!text.isTextual() || !chatId.isIntegralNumber() || !chatId.canConvertToLong()
                || chatId.longValue() == 0 || message.path("from").path("is_bot").asBoolean(false)) {
            return;
        }
        String command = text.textValue().split("\\s+", 2)[0];
        boolean start = command.equals("/start")
                || (botUsername != null && command.startsWith("/start@")
                && command.substring(7).equalsIgnoreCase(botUsername));
        if (start) {
            client.sendMessage(chatId.longValue(), messages.getMessage("telegram.welcome", null, Locale.ENGLISH));
            log.debug("Telegram welcome reply accepted");
        } else {
            log.debug("Telegram unsupported text ignored");
        }
    }
}
