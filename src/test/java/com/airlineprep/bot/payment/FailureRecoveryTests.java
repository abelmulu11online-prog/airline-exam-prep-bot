package com.airlineprep.bot.payment;
import com.airlineprep.bot.mock.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class FailureRecoveryTests extends PaymentFixture {
 @MockitoSpyBean PaymentAuditService audit;
 @MockitoSpyBean MockScoringService scoring;
 @Autowired MockAttemptService mocks;
 @BeforeEach void setup(){setupPayment();}
 @Test void failedApprovalAuditRollsBackGrantDecisionAndNotification() {
  long id=pending();doThrow(new org.springframework.dao.DataAccessResourceFailureException("fictional failure"))
   .when(org.springframework.test.util.AopTestUtils.<PaymentAuditService>getUltimateTargetObject(audit)).append(anyString(),anyString(),eq("PAYMENT_APPROVED"),anyString(),eq(id),anyString());
  assertThatThrownBy(()->review.approve(id,"test-admin")).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThat(queries.get(id).status()).isEqualTo(PaymentStatus.PENDING_REVIEW);assertThat(grant().getAccessLevel()).isEqualTo("FREE");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifetime_access_grants WHERE payment_request_id=?",Integer.class,id)).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_notifications WHERE request_id=? AND kind='USER_APPROVED'",Integer.class,id)).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_audit_events WHERE entity_id=? AND action='LIFETIME_ACCESS_GRANTED'",Integer.class,id)).isZero();
 }
 @Test void failedReceiptAuditLeavesEvidenceRetryable() {
  long id=selected();payments.reference(sender,id,"DEVTEST-ROLLBACK-"+sender);
  doThrow(new org.springframework.dao.DataAccessResourceFailureException("fictional failure"))
   .when(org.springframework.test.util.AopTestUtils.<PaymentAuditService>getUltimateTargetObject(audit)).append(anyString(),anyString(),eq("PAYMENT_RECEIPT_SUBMITTED"),anyString(),eq(id),anyString());
  assertThatThrownBy(()->payments.receipt(sender,id,receipt("rollback"))).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThat(queries.get(id).status()).isEqualTo(PaymentStatus.AWAITING_RECEIPT);assertThat(queries.get(id).receipt()).isNull();
 }
 @Test void scoringStorageFailureLeavesActiveMockAndAnswersIntact() {
  content(2);long id=mocks.prepare(sender,"failure").attempt().id();mocks.open(sender,id,0,false);mocks.answer(sender,id,0,0,0);
  doThrow(new org.springframework.dao.DataAccessResourceFailureException("fictional failure")).when(scoring).calculate(id);
  assertThatThrownBy(()->mocks.submit(sender,id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThat(mocks.introduction(sender).active().id()).isEqualTo(id);assertThat(mocks.open(sender,id,0,false).item().selected()).isZero();assertThat(grant().getMocksUsed()).isEqualTo(1);
 }
}
