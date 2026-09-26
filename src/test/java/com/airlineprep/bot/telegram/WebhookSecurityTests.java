package com.airlineprep.bot.telegram;

import com.airlineprep.bot.IsolatedDatabaseSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest @AutoConfigureMockMvc @Import(WebhookSecurityTests.WebhookConfig.class)
class WebhookSecurityTests extends IsolatedDatabaseSupport {
    @TestConfiguration(proxyBeanMethods=false)
    static class WebhookConfig {
        @Bean TelegramWebhookController testWebhook(ObjectMapper mapper) {
            return new TelegramWebhookController(new TelegramBotProperties(true,"123:fictional",TelegramBotProperties.Mode.WEBHOOK,"fictional-test-secret"),
                    mock(TelegramBotClient.class),mock(TelegramUpdateHandler.class),mapper);
        }
    }
    @Autowired MockMvc mvc;
    @Test void onlyExactWebhookPostBypassesCsrfAndLogin() throws Exception {
        mvc.perform(post(TelegramWebhookController.PATH).header(TelegramWebhookController.SECRET_HEADER,"fictional-test-secret")
                .content("{\"update_id\":1}")).andExpect(status().isOk()).andExpect(cookie().doesNotExist("JSESSIONID"));
        mvc.perform(post(TelegramWebhookController.PATH).content("{\"update_id\":1}")).andExpect(status().isForbidden());
        mvc.perform(get(TelegramWebhookController.PATH)).andExpect(status().isForbidden());
        mvc.perform(post(TelegramWebhookController.PATH+"/extra")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/settings").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/payments/1/receipt")).andExpect(status().is3xxRedirection());
    }
}
