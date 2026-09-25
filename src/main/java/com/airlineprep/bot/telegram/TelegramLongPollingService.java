package com.airlineprep.bot.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

public class TelegramLongPollingService implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TelegramLongPollingService.class);

    private final TelegramBotClient client;
    private final TelegramUpdateHandler handler;
    private volatile boolean running;
    private Thread worker;
    private long offset;

    public TelegramLongPollingService(TelegramBotClient client, TelegramUpdateHandler handler) {
        this.client = client;
        this.handler = handler;
    }

    @Override
    public synchronized void start() {
        if (worker != null && worker.isAlive()) {
            return;
        }
        running = true;
        worker = Thread.ofPlatform().name("telegram-long-polling").daemon(true).unstarted(this::poll);
        worker.start();
    }

    private void poll() {
        String username = null;
        long backoffSeconds = 2;
        log.info("Telegram polling started");
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    if (username == null) {
                        username = client.getBotUsername();
                        log.info("Telegram bot identity verified");
                    }
                    pollOnce(username);
                    backoffSeconds = 2;
                    Thread.sleep(250);
                } catch (TelegramBotClient.ApiException exception) {
                    log.warn("Telegram API/network failure (code {}); polling will back off", exception.code());
                    long delay = retryDelay(backoffSeconds, exception.retryAfterSeconds());
                    // Duration-based sleep avoids overflow when converting retry_after to milliseconds.
                    Thread.sleep(java.time.Duration.ofSeconds(delay));
                    backoffSeconds = Math.min(backoffSeconds * 2, 60);
                } catch (RuntimeException exception) {
                    log.warn("Telegram polling failure; details withheld; polling will back off");
                    Thread.sleep(backoffSeconds * 1000);
                    backoffSeconds = Math.min(backoffSeconds * 2, 60);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            running = false;
            log.info("Telegram polling stopped");
        }
    }

    void pollOnce(String username) throws InterruptedException {
        JsonNode updates = client.getUpdates(offset);
        log.debug("Telegram poll succeeded ({} updates)", updates.size());
        for (JsonNode update : updates) {
            JsonNode id = update.path("update_id");
            if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() < 0
                    || id.longValue() == Long.MAX_VALUE) {
                continue;
            }
            long updateId = id.longValue();
            if (updateId < offset) {
                continue;
            }
            try {
                handler.handle(update, username);
            } catch (TelegramBotClient.ApiException exception) {
                // An uncertain send must not trigger repeated welcome messages on this process.
                throw exception;
            } catch (RuntimeException exception) {
                log.warn("Ignoring malformed Telegram update {} (details withheld)", updateId);
            } finally {
                offset = updateId + 1;
            }
        }
    }
    static long retryDelay(long backoff,long requested) { return Math.max(backoff,Math.min(Math.max(0,requested),3600)); }

    @Override
    public void stop() {
        Thread stopping;
        synchronized (this) {
            running = false;
            stopping = worker;
        }
        if (stopping != null) {
            stopping.interrupt();
            try {
                stopping.join(5000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
