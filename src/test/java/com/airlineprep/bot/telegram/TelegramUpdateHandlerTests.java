package com.airlineprep.bot.telegram;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TelegramUpdateHandlerTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TelegramBotClient client = mock(TelegramBotClient.class);
    private final TelegramUpdateHandler handler = new TelegramUpdateHandler(client, messages());

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/start", "/start@AirlineTestBot", "/start@airlinetestbot", "/start referral"})
    void startSendsOnlyTheWelcomeMessage(String text) throws Exception {
        handler.handle(mapper.valueToTree(Map.of("message", Map.of(
                "chat", Map.of("id", 9876543210L), "text", text))), "AirlineTestBot");
        verify(client).sendMessage(9876543210L,
                "✈️ Welcome to Airline Exam Prep!\n\n"
                + "Your airline written-exam preparation journey starts here.\n\n"
                + "We are currently setting up your account experience.\n\n"
                + "More features are coming in the next development phase.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "[]", "42", "{}", "{\"message\":null}",
            "{\"callback_query\":{}}", "{\"message\":{\"photo\":[]}}",
            "{\"message\":{\"text\":123,\"chat\":{\"id\":12}}}",
            "{\"message\":{\"text\":\"/start\"}}",
            "{\"message\":{\"text\":\"/start\",\"chat\":{\"id\":\"12\"}}}",
            "{\"message\":{\"text\":\"/start\",\"chat\":{\"id\":0}}}",
            "{\"message\":{\"text\":\"/start\",\"chat\":{\"id\":999999999999999999999}}}"
    })
    void unsupportedAndMalformedUpdatesAreIgnored(String json) throws Exception {
        var update = mapper.readTree(json);
        assertDoesNotThrow(() -> handler.handle(update, "AirlineTestBot"));
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hello", "", " ", "/help", "/starting", "/start@OtherBot", "text /start"})
    void unsupportedCommandsDoNotSendAnything(String text) throws Exception {
        handler.handle(mapper.valueToTree(Map.of("message", Map.of(
                "chat", Map.of("id", 12), "text", text))), "AirlineTestBot");
        verifyNoInteractions(client);
    }

    @Test
    void nullUpdateIsIgnored() {
        assertDoesNotThrow(() -> handler.handle(null, "AirlineTestBot"));
        verifyNoInteractions(client);
    }
}
