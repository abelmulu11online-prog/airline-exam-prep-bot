package com.airlineprep.bot;

import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class IsolatedDatabaseSupport {
    @DynamicPropertySource
    static void isolate(DynamicPropertyRegistry registry) {
        String url = "jdbc:h2:mem:phase4-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> "sa");
        registry.add("spring.flyway.password", () -> "");
        registry.add("telegram.bot.enabled", () -> false);
        registry.add("telegram.bot.token", () -> "");
        registry.add("registration.phone-hmac-key", () -> "dGVzdC1vbmx5LWtleS0zMi1ieXRlcy1ub3QtYS1zZWNyZXQ=");
        registry.add("admin.bootstrap.username", () -> "");
        registry.add("admin.bootstrap.password", () -> "");
        registry.add("debug", () -> false);
    }
}
