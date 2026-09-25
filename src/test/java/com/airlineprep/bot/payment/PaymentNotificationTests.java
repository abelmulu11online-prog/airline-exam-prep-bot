package com.airlineprep.bot.payment;
import java.util.Map;
import com.airlineprep.bot.telegram.TelegramBotClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest(properties={"telegram.admin.id=999000123","payment.notifications.automatic=false"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentNotificationTests extends PaymentFixture {
 @MockitoBean TelegramBotClient client;
 @MockitoBean java.time.Clock clock;
 @Autowired PaymentNotificationDispatcher dispatcher;
 @BeforeEach void setup() {
  // Exact database timestamp precision keeps immediate retries deterministic.
  when(clock.instant()).thenReturn(java.time.Instant.parse("2026-01-01T00:00:00Z"));
  setupPayment();
 }
 @Test void adminAndUserNotificationsRunAfterCommitAndRetryIsIdempotent() throws Exception {
  doAnswer(invocation->{
   assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_requests WHERE status='PENDING_REVIEW'",Integer.class)).isPositive();
   return null;
  }).when(client).sendMessage(eq(999000123L),anyString(),anyMap());
  long id=pending();
  dispatcher.retryDue();
  verify(client).sendMessage(eq(999000123L),contains("New payment waiting for review"),anyMap());
  review.approve(id,"test-admin");review.approve(id,"test-admin");
  dispatcher.retryDue();
  verify(client,times(1)).sendMessage(eq(sender),contains("Lifetime access is now active"),anyMap());
  long notification=jdbc.queryForObject("SELECT id FROM payment_notifications WHERE request_id=? AND kind='USER_APPROVED'",Long.class,id);
  dispatcher.deliver(notification);
  verify(client,times(1)).sendMessage(eq(sender),anyString(),anyMap());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_notifications WHERE request_id=? AND status='SENT'",Integer.class,id)).isEqualTo(2);
 }
 @Test void failedAdminAndApprovalNotificationsNeverUndoBusinessState() throws Exception {
  doThrow(mock(TelegramBotClient.ApiException.class)).when(client).sendMessage(anyLong(),anyString(),anyMap());
  long id=pending();dispatcher.retryDue();assertThat(queries.get(id).status()).isEqualTo(PaymentStatus.PENDING_REVIEW);
  review.approve(id,"test-admin");dispatcher.retryDue();assertThat(grant().getAccessLevel()).isEqualTo("LIFETIME");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_notifications WHERE request_id=? AND status='FAILED'",Integer.class,id)).isEqualTo(2);
  reset(client);dispatcher.retry(id,"test-admin");dispatcher.retryDue();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_notifications WHERE request_id=? AND status='SENT'",Integer.class,id)).isEqualTo(2);
 }
 @Test void failedRejectionNotificationPreservesRejection() throws Exception {
  long id=pending();doThrow(mock(TelegramBotClient.ApiException.class)).when(client).sendMessage(eq(sender),anyString(),anyMap());
  review.reject(id,"test-admin","No transaction found");
  dispatcher.retryDue();
  assertThat(queries.get(id).status()).isEqualTo(PaymentStatus.REJECTED);assertThat(grant().getAccessLevel()).isEqualTo("FREE");
 }
}
