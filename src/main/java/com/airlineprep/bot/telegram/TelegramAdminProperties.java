package com.airlineprep.bot.telegram;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
@Validated @ConfigurationProperties("telegram.admin")
public record TelegramAdminProperties(@Positive Long id) {}
