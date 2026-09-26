package com.airlineprep.bot.telegram;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("telegram.bot")
public record TelegramBotProperties(boolean enabled, String token, Mode mode, String webhookSecret) {
    public enum Mode { POLLING, WEBHOOK }

    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public TelegramBotProperties {
        if (mode == null) mode = Mode.POLLING;
    }

    public TelegramBotProperties(boolean enabled, String token) {
        this(enabled, token, Mode.POLLING, null);
    }

    @AssertTrue(message = "TELEGRAM_WEBHOOK_SECRET must contain 1-256 URL-safe characters in WEBHOOK mode")
    public boolean isWebhookSecretConfigured() {
        return !enabled || mode != Mode.WEBHOOK
                || (webhookSecret != null && webhookSecret.matches("[A-Za-z0-9_-]{1,256}"));
    }

    @AssertTrue(message = "TELEGRAM_BOT_TOKEN must be configured when Telegram is enabled")
    public boolean isTokenConfigured() {
        return !enabled || (token != null && token.matches("[0-9]+:[A-Za-z0-9_-]+"));
    }

    @Override
    public String toString() {
        return "TelegramBotProperties[enabled=" + enabled + ", mode=" + mode
                + ", token=<redacted>, webhookSecret=<redacted>]";
    }
}
