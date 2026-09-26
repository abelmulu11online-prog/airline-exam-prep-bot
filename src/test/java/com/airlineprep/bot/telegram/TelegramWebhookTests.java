package com.airlineprep.bot.telegram;

import java.util.List;
import java.util.Map;
import com.airlineprep.bot.user.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TelegramWebhookTests {
    static final String SECRET = "fictional-webhook-unit-test";
    final ObjectMapper mapper = new ObjectMapper();
    final TelegramBotClient client = mock(TelegramBotClient.class);
    final RegistrationService registration = mock(RegistrationService.class);
    final RegistrationPresenter presenter = mock(RegistrationPresenter.class);
    final TelegramUpdateHandler handler = spy(new TelegramUpdateHandler(client, registration, presenter, mock(StudentFlow.class)));
    final TelegramWebhookController controller = new TelegramWebhookController(
            new TelegramBotProperties(true, "123:fictional", TelegramBotProperties.Mode.WEBHOOK, SECRET), client, handler, mapper);
    final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

    void deliver(String body, int expected) throws Exception {
        mvc.perform(post(TelegramWebhookController.PATH).header(TelegramWebhookController.SECRET_HEADER, SECRET)
                .contentType("application/json").content(body)).andExpect(status().is(expected));
    }

    @ParameterizedTest @ValueSource(strings={"", " ", "incorrect"})
    void rejectsBlankAndIncorrectSecretBeforeParsing(String secret) throws Exception {
        mvc.perform(post(TelegramWebhookController.PATH).header(TelegramWebhookController.SECRET_HEADER, secret)
                .content("invalid JSON")).andExpect(status().isForbidden());
        verifyNoInteractions(handler, registration, client);
    }
    @Test void rejectsAbsentAndDuplicateHeader() throws Exception {
        mvc.perform(post(TelegramWebhookController.PATH).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(TelegramWebhookController.PATH).header(TelegramWebhookController.SECRET_HEADER, SECRET, SECRET)
                .content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(handler);
    }
    @ParameterizedTest @ValueSource(strings={"", "{", "null", "[]", "1", "{}", "{\"update_id\":-1}",
            "{\"update_id\":1.1}", "{\"update_id\":\"1\"}", "{\"update_id\":9223372036854775807}", "{\"update_id\":1} {}"})
    void malformedEnvelopeIsControlled(String json) throws Exception { deliver(json,400); verifyNoInteractions(handler); }

    @ParameterizedTest @ValueSource(strings={"{\"update_id\":1}","{\"update_id\":2,\"callback_query\":{}}",
            "{\"update_id\":3,\"message\":{\"text\":\"/start\"}}", "{\"update_id\":4,\"poll\":{}}"})
    void unsupportedOrMissingFieldsAreSafelyIgnored(String json) throws Exception { deliver(json,200); verifyNoInteractions(registration); }

    @ParameterizedTest @ValueSource(strings={"group","supergroup","channel"})
    void groupBoundaryMatchesPolling(String type) throws Exception {
        deliver(mapper.writeValueAsString(Map.of("update_id",1,"message",Map.of("from",Map.of("id",12),
                "chat",Map.of("id",12,"type",type),"text","/start"))),200);
        verifyNoInteractions(registration);
    }
    @Test void validStartUsesTheSameCoreAsPollingWithoutRemoteIdentityLookup() throws Exception {
        var update=mapper.readTree("{\"update_id\":1,\"message\":{\"from\":{\"id\":12},\"chat\":{\"id\":12,\"type\":\"private\"},\"text\":\"/start\"}}");
        when(registration.start(12)).thenReturn(new RegistrationView(RegistrationStatus.LANGUAGE_REQUIRED,"en",List.of(),null,null,null,null));
        handler.handle(update,"TestBot"); deliver(update.toString(),200);
        verify(registration,times(2)).start(12); verify(presenter,times(2)).show(eq(12L),any());
        verify(client,never()).getBotUsername();
    }
    @Test void callbackAndForwardedContactRetainHandlerProtections() throws Exception {
        when(registration.language(12,"en")).thenReturn(new RegistrationView(RegistrationStatus.EXAM_TYPE_REQUIRED,"en",List.of(),null,null,null,null));
        deliver("{\"update_id\":1,\"callback_query\":{\"id\":\"cb\",\"data\":\"lang:en\",\"from\":{\"id\":12},\"message\":{\"chat\":{\"id\":12,\"type\":\"private\"}}}}",200);
        verify(registration).language(12,"en");
        when(registration.contact(12,null,null)).thenReturn(new RegistrationView(RegistrationStatus.PHONE_REQUIRED,"en",List.of(),null,null,null,null));
        deliver("{\"update_id\":2,\"message\":{\"from\":{\"id\":12},\"chat\":{\"id\":12,\"type\":\"private\"},\"forward_origin\":{},\"contact\":{\"user_id\":12,\"phone_number\":\"0912345678\"}}}",200);
        verify(registration).contact(12,null,null);
    }
    @Test void oversizedInputIsRejectedEvenWithoutContentLength() throws Exception {
        deliver("x".repeat(256*1024+1),413);
        var request=new MockHttpServletRequest("POST",TelegramWebhookController.PATH) {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.addHeader(TelegramWebhookController.SECRET_HEADER,SECRET);request.setContent(new byte[256*1024+1]);
        assertThat(controller.receive(request).getStatusCode().value()).isEqualTo(413);verifyNoInteractions(handler);
    }
    @Test void storageFailureReturnsRetryableStatusWithoutPrivateDetails() throws Exception {
        when(registration.start(12)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private SQL marker"));
        deliver("{\"update_id\":1,\"message\":{\"from\":{\"id\":12},\"chat\":{\"id\":12,\"type\":\"private\"},\"text\":\"/start\"}}",503);
        verifyNoInteractions(presenter);
    }
    @Test void identityIsCachedForAddressedStartOnly() throws Exception {
        when(client.getBotUsername()).thenReturn("TestBot");
        when(registration.start(12)).thenReturn(new RegistrationView(RegistrationStatus.LANGUAGE_REQUIRED,"en",List.of(),null,null,null,null));
        String body="{\"update_id\":1,\"message\":{\"from\":{\"id\":12},\"chat\":{\"id\":12,\"type\":\"private\"},\"text\":\"/start@TestBot\"}}";
        deliver(body,200);deliver(body,200);verify(client).getBotUsername();verify(registration,times(2)).start(12);
    }

    @Test void backpressureDoesNotAcknowledgeUnprocessedWork() throws Exception {
        var blocked=new java.util.concurrent.CountDownLatch(2);
        var release=new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation->{blocked.countDown();release.await();return null;}).when(handler).handleWebhook(any(),any());
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->{deliver("{\"update_id\":1}",200);return null;});
            var second=executor.submit(()->{deliver("{\"update_id\":2}",200);return null;});
            try {
                assertThat(blocked.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                deliver("{\"update_id\":3}",503);
            } finally { release.countDown(); }
            first.get(5,java.util.concurrent.TimeUnit.SECONDS);second.get(5,java.util.concurrent.TimeUnit.SECONDS);
        }
        deliver("{\"update_id\":3}",200);
    }
}
