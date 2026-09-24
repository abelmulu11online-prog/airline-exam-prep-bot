package com.airlineprep.bot;

import java.sql.Connection;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;
import com.airlineprep.bot.telegram.TelegramBotClient;
import com.airlineprep.bot.telegram.TelegramLongPollingService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FoundationTests {

    private final MockMvc mvc;
    private final ApplicationContext context;
    private final DataSource dataSource;
    private final Flyway flyway;

    FoundationTests(MockMvc mvc, ApplicationContext context, DataSource dataSource, Flyway flyway) {
        this.mvc = mvc;
        this.context = context;
        this.dataSource = dataSource;
        this.flyway = flyway;
    }

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        // These test-only properties take precedence over developer environment variables.
        String url = "jdbc:h2:mem:foundation-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> "sa");
        registry.add("spring.flyway.password", () -> "");
        registry.add("telegram.bot.enabled", () -> false);
        registry.add("telegram.bot.token", () -> "");
    }

    @Test
    void applicationInitializesJpaAndFlywayOnIsolatedDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:foundation-");
        }
        assertThat(context.getBean(EntityManagerFactory.class).isOpen()).isTrue();
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:foundation-");
        }
        assertThat(flyway.getConfiguration().isCleanDisabled()).isTrue();
        flyway.validate();
    }

    @Test
    void healthIsPublicAndDoesNotExposeDatabaseDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/admin", "/login", "/actuator/env", "/actuator/info"})
    void otherRoutesAreDeniedWithoutLoginRedirect(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void noGeneratedLoginUserExists() {
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
    }

    @Test
    void telegramDisabledLoadsWithoutTokenOrNetworkComponents() {
        assertThat(context.getBeansOfType(TelegramBotClient.class)).isEmpty();
        assertThat(context.getBeansOfType(TelegramLongPollingService.class)).isEmpty();
    }
}
