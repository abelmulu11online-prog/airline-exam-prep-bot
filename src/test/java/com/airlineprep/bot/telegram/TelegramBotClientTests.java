package com.airlineprep.bot.telegram;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramBotClientTests {
    // Synthetic marker used only with a mocked HTTP client, never a real credential.
    private static final String TOKEN = "123456:unit-test-placeholder";
    private final HttpClient http = mock(HttpClient.class);
    private final TelegramBotClient client = new TelegramBotClient(
            new TelegramBotProperties(true, TOKEN), http, new ObjectMapper());
    private HttpResponse<String> response;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void mockTransport() throws Exception {
        response = mock(HttpResponse.class);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers
                .<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
    }

    @Test
    void pollingUsesHttpsAndValidatesResponse() throws Exception {
        when(response.body()).thenReturn("{\"ok\":true,\"result\":[{\"update_id\":42}]}");
        assertThat(client.getUpdates(42).get(0).path("update_id").asLong()).isEqualTo(42);
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any());
        assertThat(request.getValue().uri().getScheme()).isEqualTo("https");
        assertThat(request.getValue().uri().getHost()).isEqualTo("api.telegram.org");
        assertThat(request.getValue().method()).isEqualTo("POST");
        assertThat(request.getValue().timeout()).contains(java.time.Duration.ofSeconds(40));
    }

    @Test
    void identityIsReadFromTelegram() throws Exception {
        when(response.body()).thenReturn(
                "{\"ok\":true,\"result\":{\"is_bot\":true,\"username\":\"AirlineTestBot\"}}");
        assertThat(client.getBotUsername()).isEqualTo("AirlineTestBot");
    }

    @Test
    void rateLimitRetainsOnlySafeCodeAndRetryDelay() {
        when(response.statusCode()).thenReturn(429);
        when(response.body()).thenReturn("{\"ok\":false,\"error_code\":429,\"description\":\""
                + TOKEN + "\",\"parameters\":{\"retry_after\":17}}");
        var failure = assertThrows(TelegramBotClient.ApiException.class, () -> client.getUpdates(0));
        assertThat(failure.code()).isEqualTo(429);
        assertThat(failure.retryAfterSeconds()).isEqualTo(17);
        assertThat(failure).hasMessageNotContaining(TOKEN).hasNoCause();
    }

    @Test
    void networkErrorsDiscardTokenBearingCause() throws Exception {
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers
                .<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new IOException("https://api.telegram.org/bot" + TOKEN + "/getUpdates"));
        var failure = assertThrows(TelegramBotClient.ApiException.class, () -> client.getUpdates(0));
        assertThat(failure).hasMessageNotContaining(TOKEN).hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not JSON", "null", "[]", "{}", "{\"ok\":true}",
            "{\"ok\":\"true\",\"result\":[]}", "{\"ok\":false,\"result\":[]}",
            "{\"ok\":true,\"result\":{}}"})
    void malformedResponsesAreSafelyRejected(String body) {
        when(response.body()).thenReturn(body);
        var failure = assertThrows(TelegramBotClient.ApiException.class, () -> client.getUpdates(0));
        assertThat(failure).hasMessage("Telegram request failed (details withheld)").hasNoCause();
    }

    @Test
    void unsuccessfulHttpStatusCannotMasqueradeAsSuccess() {
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("{\"ok\":true,\"result\":[]}");
        assertThrows(TelegramBotClient.ApiException.class, () -> client.getUpdates(0));
    }

    @Test
    void sendResponseMustContainAMessage() {
        when(response.body()).thenReturn("{\"ok\":true,\"result\":true}");
        assertThrows(TelegramBotClient.ApiException.class, () -> client.sendMessage(12, "Welcome"));
    }

    @Test
    void interruptedRequestsRemainInterruptible() throws Exception {
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers
                .<HttpResponse.BodyHandler<String>>any())).thenThrow(new InterruptedException());
        assertThrows(InterruptedException.class, () -> client.getUpdates(0));
    }
}
