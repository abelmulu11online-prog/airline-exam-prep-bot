package com.airlineprep.bot.telegram;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("telegram.bot")
public record TelegramBotProperties(boolean enabled, String token) {

    @AssertTrue(message = "TELEGRAM_BOT_TOKEN must be configured when Telegram is enabled")
    public boolean isTokenConfigured() {
        return !enabled || (token != null && token.matches("[0-9]+:[A-Za-z0-9_-]+"));
    }

    @Override
    public String toString() {
        return "TelegramBotProperties[enabled=" + enabled + ", token=<redacted>]";
    }
}
