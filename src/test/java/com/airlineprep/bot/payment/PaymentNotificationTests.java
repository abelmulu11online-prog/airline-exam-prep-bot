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
 @Test void concurrentClaimsSendOnceAndDoNotHoldDatabaseTransaction() throws Exception {
  long id=pending();long notification=notification(id);
  var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
  doAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();entered.countDown();assertThat(release.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();return null;}).when(client).sendMessage(anyLong(),anyString(),anyMap());
  try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
   var first=executor.submit(()->dispatcher.deliver(notification));
   try {assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();executor.submit(()->dispatcher.deliver(notification)).get(10,java.util.concurrent.TimeUnit.SECONDS);}
   finally {release.countDown();}
   first.get(10,java.util.concurrent.TimeUnit.SECONDS);
  }
  verify(client,times(1)).sendMessage(anyLong(),anyString(),anyMap());
 }
 @Test void staleWorkerCannotOverwriteNewLeaseOutcome() throws Exception {
  long id=pending();long notification=notification(id);
  doAnswer(call->{jdbc.update("UPDATE payment_notifications SET claim_token='new-worker',status='SENDING' WHERE id=?",notification);return null;}).when(client).sendMessage(anyLong(),anyString(),anyMap());
  dispatcher.deliver(notification);
  assertThat(jdbc.queryForObject("SELECT status FROM payment_notifications WHERE id=?",String.class,notification)).isEqualTo("SENDING");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_audit_events WHERE entity_id=? AND action='ADMIN_NOTIFICATION_SENT'",Integer.class,id)).isZero();
 }
 @Test void abandonedFifthLeaseBecomesRetryableWithoutReapproving() throws Exception {
  long id=pending();long notification=notification(id);
  jdbc.update("UPDATE payment_notifications SET status='SENDING',attempts=5,claim_token='abandoned' WHERE id=?",notification);
  dispatcher.retryDue();assertThat(jdbc.queryForObject("SELECT status FROM payment_notifications WHERE id=?",String.class,notification)).isEqualTo("FAILED");
  dispatcher.retry(id,"test-admin");dispatcher.retryDue();
  assertThat(jdbc.queryForObject("SELECT status FROM payment_notifications WHERE id=?",String.class,notification)).isEqualTo("SENT");
  assertThat(queries.get(id).status()).isEqualTo(PaymentStatus.PENDING_REVIEW);
 }
 @Test void rateLimitRespectsRetryAfterAndBlockedUsersStopAfterFiveAttempts() throws Exception {
  long id=pending();long notification=notification(id);var failure=mock(TelegramBotClient.ApiException.class);
  when(failure.code()).thenReturn(429);when(failure.retryAfterSeconds()).thenReturn(180L);
  doThrow(failure).when(client).sendMessage(anyLong(),anyString(),anyMap());dispatcher.deliver(notification);
  assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM payment_notifications WHERE id=?",java.sql.Timestamp.class,notification).toInstant()).isEqualTo(java.time.Instant.parse("2026-01-01T00:03:00Z"));
  when(failure.code()).thenReturn(403);when(failure.retryAfterSeconds()).thenReturn(0L);
  for(int i=1;i<=6;i++){when(clock.instant()).thenReturn(java.time.Instant.parse("2026-01-01T00:00:00Z").plusSeconds(400L*i));dispatcher.deliver(notification);}
  verify(client,times(5)).sendMessage(anyLong(),anyString(),anyMap());assertThat(queries.get(id).status()).isEqualTo(PaymentStatus.PENDING_REVIEW);
 }
 private long notification(long payment) {return jdbc.queryForObject("SELECT id FROM payment_notifications WHERE request_id=? AND kind='ADMIN_PENDING'",Long.class,payment);}
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
