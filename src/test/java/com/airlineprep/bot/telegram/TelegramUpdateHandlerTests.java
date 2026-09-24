package com.airlineprep.bot.telegram;

import java.util.List;
import java.util.Map;
import com.airlineprep.bot.user.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TelegramUpdateHandlerTests {
    final ObjectMapper mapper = new ObjectMapper();
    final TelegramBotClient client = mock(TelegramBotClient.class);
    final RegistrationService registration = mock(RegistrationService.class);
    final RegistrationPresenter presenter = new RegistrationPresenter(client, messages());
    final TelegramUpdateHandler handler = new TelegramUpdateHandler(client, registration, presenter);
    static ResourceBundleMessageSource messages() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages"); source.setDefaultEncoding("UTF-8"); return source;
    }
    RegistrationView view(RegistrationStatus state) {
        return new RegistrationView(state, "en", List.of(), null, 100, 2, 50);
    }
    JsonNode message(String text) {
        return mapper.valueToTree(Map.of("message", Map.of("chat", Map.of("id", 12, "type", "private"),
            "from", Map.of("id", 12), "text", text)));
    }
    JsonNode callback(String data) {
        return mapper.valueToTree(Map.of("callback_query", Map.of("id", "query", "data", data,
            "from", Map.of("id", 12), "message", Map.of("chat", Map.of("id", 12, "type", "private")))));
    }
    @ParameterizedTest @ValueSource(strings = {"/start", "/start@AirlineTestBot", "/start@airlinetestbot", "/start referral"})
    void startPreservesWelcomeAndBeginsRegistration(String text) throws Exception {
        when(registration.start(12)).thenReturn(view(RegistrationStatus.LANGUAGE_REQUIRED));
        handler.handle(message(text), "AirlineTestBot");
        verify(client).sendMessage(eq(12L), contains("Welcome to Airline Exam Prep!"),
            argThat(m -> m.containsKey("inline_keyboard")));
    }
    @ParameterizedTest @ValueSource(strings = {"null","[]","42","{}","{\"message\":null}","{\"callback_query\":{}}",
        "{\"message\":{\"photo\":[]}}","{\"message\":{\"text\":123,\"chat\":{\"id\":12}}}",
        "{\"message\":{\"text\":\"/start\"}}","{\"message\":{\"text\":\"/start\",\"chat\":{\"id\":\"12\"}}}"})
    void malformedUpdatesAreIgnored(String json) throws Exception {
        handler.handle(mapper.readTree(json), "AirlineTestBot");
        verifyNoInteractions(registration);
    }
    @ParameterizedTest @ValueSource(strings = {"hello", "", " ", "/help", "/starting", "/start@OtherBot", "text /start", "0912345678"})
    void unsupportedTextRemainsSafe(String text) throws Exception {
        handler.handle(message(text), "AirlineTestBot");
        verifyNoInteractions(registration, client);
    }
    @Test void nullUpdateIsIgnored() throws Exception { handler.handle(null, "AirlineTestBot"); verifyNoInteractions(client); }
    @Test void callbacksAreAcknowledgedAndLanguagePersisted() throws Exception {
        when(registration.language(12, "am")).thenReturn(view(RegistrationStatus.EXAM_TYPE_REQUIRED));
        handler.handle(callback("lang:am"), "AirlineTestBot");
        verify(client).answerCallbackQuery("query"); verify(registration).language(12, "am");
    }
    @ParameterizedTest @ValueSource(strings = {"lang:xx", "exam:-1", "exam:999999999999999999999999", "broken"})
    void invalidCallbacksAreAcknowledgedAndIgnored(String data) throws Exception {
        handler.handle(callback(data), "AirlineTestBot");
        verify(client).answerCallbackQuery("query"); verifyNoInteractions(registration);
    }
    @Test void examCallbackRequestsOwnContact() throws Exception {
        when(registration.exam(12, 7)).thenReturn(view(RegistrationStatus.PHONE_REQUIRED));
        handler.handle(callback("exam:7"), "AirlineTestBot");
        verify(client).sendMessage(eq(12L), contains("OWN"), argThat(m -> m.toString().contains("request_contact=true")));
    }
    @Test void contactPassesSenderAndOwnerSeparately() throws Exception {
        when(registration.contact(12, 99L, "0912345678")).thenReturn(view(RegistrationStatus.PHONE_REQUIRED));
        handler.handle(mapper.valueToTree(Map.of("message", Map.of("chat", Map.of("id",12,"type","private"),
            "from",Map.of("id",12),"contact",Map.of("user_id",99,"phone_number","0912345678")))), "AirlineTestBot");
        verify(registration).contact(12, 99L, "0912345678");
    }
    @ParameterizedTest @ValueSource(strings = {"group","supergroup","channel"})
    void registrationNeverRunsInGroups(String type) throws Exception {
        var update = (com.fasterxml.jackson.databind.node.ObjectNode) message("/start");
        ((com.fasterxml.jackson.databind.node.ObjectNode) update.path("message").path("chat")).put("type",type);
        handler.handle(update,"AirlineTestBot"); verifyNoInteractions(registration,client);
    }
    @Test void unavailableDatabaseGetsSafeRetryMessage() throws Exception {
        when(registration.start(12)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private details"));
        handler.handle(message("/start"),"AirlineTestBot");
        verify(client).sendMessage(eq(12L),contains("retry"));
    }
    @Test void noExamsGetsSafeMessage() throws Exception {
        presenter.show(12,view(RegistrationStatus.EXAM_TYPE_REQUIRED));
        verify(client).sendMessage(eq(12L),contains("temporarily unavailable"),anyMap());
    }
    @Test void amharicPromptsAndSuccessUseSelectedLanguage() throws Exception {
        presenter.show(12,new RegistrationView(RegistrationStatus.PHONE_REQUIRED,"am",List.of(),null,null,null,null));
        verify(client).sendMessage(eq(12L),contains("የኢትዮጵያ"),anyMap());
        presenter.show(12,new RegistrationView(RegistrationStatus.COMPLETED,"am",List.of(),null,100,2,50));
        verify(client).sendMessage(eq(12L),contains("ተጠናቋል"),eq(Map.of("remove_keyboard",true)));
    }
}
