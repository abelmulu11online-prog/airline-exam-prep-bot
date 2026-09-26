package com.airlineprep.bot;

import java.net.URI;
import java.net.http.*;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties={"PORT=10000","SERVER_PORT=10001","payment.notifications.automatic=false"})
@ActiveProfiles("prod") @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ProductionProfileTests {
    @org.springframework.test.context.DynamicPropertySource
    static void isolate(org.springframework.test.context.DynamicPropertyRegistry registry) {
        IsolatedDatabaseSupport.isolate(registry);
        registry.add("telegram.bot.enabled", () -> true);
        registry.add("telegram.bot.token", () -> "123:fictional-test");
        registry.add("telegram.bot.webhook-secret", () -> "fictional-prod-profile-test");
        registry.add("telegram.bot.mode", () -> "WEBHOOK");
    }
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.airlineprep.bot.telegram.TelegramBotClient telegramClient;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired ServerProperties server;
    @Autowired HikariDataSource pool;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.airlineprep.bot.admin.AdminUserRepository admins;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:10000"+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void renderPortHealthCookiesAndHeadersWithRealHttp() throws Exception {
        assertThat(server.getPort()).isEqualTo(10000);
        assertThat(server.getAddress().getHostAddress()).isEqualTo("0.0.0.0");
        var health=get("/actuator/health");assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).isEqualTo("{\"status\":\"UP\"}");
        for(String endpoint:new String[]{"env","configprops","heapdump","beans","mappings","threaddump"})
            assertThat(get("/actuator/"+endpoint).statusCode()).isEqualTo(403);
        var login=get("/admin/login");assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().firstValue("Set-Cookie").orElseThrow()).contains("Secure","HttpOnly","SameSite=Lax");
        assertThat(login.headers().firstValue("Content-Security-Policy").orElseThrow()).contains("script-src 'none'");
        var anonymous=get("/admin");assertThat(anonymous.statusCode()).isEqualTo(302);
        assertThat(anonymous.headers().firstValue("Location").orElseThrow()).isEqualTo("/admin/login");
        assertThat(context.getBeansOfType(com.airlineprep.bot.telegram.TelegramLongPollingService.class)).isEmpty();
        assertThat(context.getBeansOfType(com.airlineprep.bot.telegram.TelegramWebhookController.class)).hasSize(1);
        var webhook=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:10000/api/telegram/webhook"))
                .header("X-Telegram-Bot-Api-Secret-Token","fictional-prod-profile-test")
                .POST(HttpRequest.BodyPublishers.ofString("{\"update_id\":1}")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(webhook.statusCode()).isEqualTo(200);
        assertThat(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:10000/admin/login"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }
    @Test void adminLoginAndLogoutKeepRelativeRedirectsBehindTlsProxy() throws Exception {
        var admin=new com.airlineprep.bot.admin.AdminUser();admin.setUsername("prod-test-admin");
        admin.setPasswordHash(encoder.encode("fictional-prod-password"));admin.setEnabled(true);admins.saveAndFlush(admin);
        var login=get("/admin/login");
        var matcher=java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
        assertThat(matcher.find()).isTrue();String csrf=matcher.group(1);
        String cookie=login.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
        // Deliberately carry the Secure cookie over loopback HTTP in this test only.
        var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:10000/admin/login"))
                .header("Cookie",cookie).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=prod-test-admin&password=fictional-prod-password&_csrf="+
                        java.net.URLEncoder.encode(csrf,java.nio.charset.StandardCharsets.UTF_8))).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow()).isEqualTo("/admin");
        cookie=response.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
        var dashboard=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:10000/admin"))
                .header("Cookie",cookie).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(dashboard.statusCode()).isEqualTo(200);
        matcher=java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(dashboard.body());
        assertThat(matcher.find()).isTrue();
        var logout=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:10000/admin/logout"))
                .header("Cookie",cookie).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("_csrf="+java.net.URLEncoder.encode(matcher.group(1),
                        java.nio.charset.StandardCharsets.UTF_8))).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(logout.statusCode()).isEqualTo(302);
        assertThat(logout.headers().firstValue("Location").orElseThrow()).isEqualTo("/admin/login?logout");
    }
    @Test void conservativePoolReacquiresConnectionsAfterEviction() throws Exception {
        assertThat(pool.getMaximumPoolSize()).isEqualTo(4);assertThat(pool.getMinimumIdle()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT 1",Integer.class)).isEqualTo(1);
        java.sql.Connection old;
        try(var c=pool.getConnection()) { old=c.unwrap(org.h2.jdbc.JdbcConnection.class); }
        pool.getHikariPoolMXBean().softEvictConnections();
        try(var c=pool.getConnection()) { assertThat(c.unwrap(org.h2.jdbc.JdbcConnection.class)).isNotSameAs(old); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_settings",Integer.class)).isEqualTo(1);
    }
}
