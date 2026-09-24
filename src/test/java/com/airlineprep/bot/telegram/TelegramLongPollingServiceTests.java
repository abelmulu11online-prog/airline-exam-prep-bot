package com.airlineprep.bot.telegram;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class TelegramLongPollingServiceTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TelegramBotClient client = mock(TelegramBotClient.class);
    private final TelegramUpdateHandler handler = mock(TelegramUpdateHandler.class);
    private final TelegramLongPollingService service = new TelegramLongPollingService(client, handler);

    @Test
    void offsetAdvancesAndRepeatedUpdatesAreSkipped() throws Exception {
        var batch = mapper.readTree("[{\"update_id\":7},{\"update_id\":8}]");
        when(client.getUpdates(0)).thenReturn(batch);
        when(client.getUpdates(9)).thenReturn(batch);
        service.pollOnce("AirlineTestBot");
        service.pollOnce("AirlineTestBot");
        verify(handler, times(2)).handle(any(), eq("AirlineTestBot"));
        verify(client).getUpdates(9);
    }

    @Test
    void malformedUpdateDoesNotPreventLaterUpdate(CapturedOutput output) throws Exception {
        var bad = mapper.readTree("{\"update_id\":3}");
        var good = mapper.readTree("{\"update_id\":4}");
        when(client.getUpdates(0)).thenReturn(mapper.createArrayNode().addNull().add(bad).add(good));
        doThrow(new IllegalArgumentException("private-message-marker"))
                .when(handler).handle(bad, "AirlineTestBot");
        service.pollOnce("AirlineTestBot");
        verify(handler).handle(good, "AirlineTestBot");
        assertThat(output.getAll()).doesNotContain("private-message-marker");
    }

    @Test
    void failedSendIsNotRepeatedAndLaterUpdatesRemainPending() throws Exception {
        var failed = mapper.readTree("{\"update_id\":10}");
        var later = mapper.readTree("{\"update_id\":11}");
        when(client.getUpdates(0)).thenReturn(mapper.createArrayNode().add(failed).add(later));
        when(client.getUpdates(11)).thenReturn(mapper.createArrayNode().add(failed).add(later));
        doThrow(new TelegramBotClient.ApiException(429, 5)).when(handler).handle(failed, "AirlineTestBot");
        assertThrows(TelegramBotClient.ApiException.class, () -> service.pollOnce("AirlineTestBot"));
        service.pollOnce("AirlineTestBot");
        verify(handler, times(1)).handle(failed, "AirlineTestBot");
        verify(handler).handle(later, "AirlineTestBot");
    }

    @Test
    void startIsIdempotentAndShutdownInterruptsInFlightPolling() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(client.getBotUsername()).thenReturn("AirlineTestBot");
        when(client.getUpdates(anyLong())).thenAnswer(invocation -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
                return mapper.createArrayNode();
            } catch (InterruptedException exception) {
                interrupted.countDown();
                throw exception;
            }
        });
        try {
            service.start();
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            service.start();
            assertThat(service.isRunning()).isTrue();
        } finally {
            service.stop();
        }
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(service.isRunning()).isFalse();
        verify(client, times(1)).getBotUsername();
    }

    @Test
    void apiFailureBacksOffAndShutdownInterruptsWait(CapturedOutput output) throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        when(client.getBotUsername()).thenAnswer(invocation -> {
            failed.countDown();
            throw new TelegramBotClient.ApiException(429, 30);
        });
        try {
            service.start();
            assertThat(failed.await(3, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(150);
            verify(client, times(1)).getBotUsername();
        } finally {
            service.stop();
        }
        assertThat(service.isRunning()).isFalse();
        assertThat(output.getAll()).contains("code 429").doesNotContain("api.telegram.org");
    }
}
