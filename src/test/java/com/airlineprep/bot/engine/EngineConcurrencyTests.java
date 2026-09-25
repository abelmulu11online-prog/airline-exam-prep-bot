package com.airlineprep.bot.engine;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.airlineprep.bot.practice.*;
import com.airlineprep.bot.mock.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class EngineConcurrencyTests extends EngineFixture {
 @Autowired PracticeService practice;
 @Autowired MockAttemptService mocks;
 @Autowired StudentProgressService progress;
 @Autowired PlatformTransactionManager transactions;
 @BeforeEach void setup() { prepareFixture(2);content(3); }
 <T> List<T> race(Supplier<T> first,Supplier<T> second) throws Exception {
  var gate=new CountDownLatch(1);
  try(var executor=Executors.newFixedThreadPool(2)) {
   var a=executor.submit(()->{gate.await();return first.get();});
   var b=executor.submit(()->{gate.await();return second.get();});gate.countDown();
   return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
  }
 }
 @Test void concurrentPracticeAnswersConsumeOnce() throws Exception {
  long id=practice.next(sender,null,null,false).delivery().id();
  var views=race(()->practice.answer(sender,id,0),()->practice.answer(sender,id,1));
  assertThat(views.get(0).delivery().selected()).isEqualTo(views.get(1).delivery().selected());
  assertThat(grant().getPracticeUsed()).isEqualTo(1);assertThat(progress.get(sender).answered()).isEqualTo(1);
 }
 @Test void concurrentCreationFirstAnswersAndSubmissionsAreStable() throws Exception {
  var created=race(()->mocks.prepare(sender,"one"),()->mocks.prepare(sender,"two"));
  long id=created.getFirst().attempt().id();assertThat(created.getLast().attempt().id()).isEqualTo(id);
  mocks.open(sender,id,0,false);
  race(()->mocks.answer(sender,id,0,0,0),()->mocks.answer(sender,id,1,1,0));
  assertThat(grant().getMocksUsed()).isEqualTo(1);
  var results=race(()->mocks.submit(sender,id),()->mocks.submit(sender,id));
  assertThat(results.getFirst().score()).isEqualTo(results.getLast().score());
  assertThat(results.getFirst().score().correct()).isEqualTo(1);
 }
 @Test void answerVersusSubmitProducesCoherentFinalScore() throws Exception {
  long id=mocks.prepare(sender,"race").attempt().id();mocks.open(sender,id,0,false);mocks.answer(sender,id,0,0,0);
  race(()->mocks.answer(sender,id,1,0,0),()->mocks.submit(sender,id));
  var finalScore=mocks.submit(sender,id).score();
  assertThat(finalScore.correct()).isBetween(1,2);assertThat(finalScore.correct()+finalScore.unanswered()).isEqualTo(2);
  assertThat(grant().getMocksUsed()).isEqualTo(1);
 }
 @Test void rollbackRestoresBothAnswerAndEntitlement() {
  long delivery=practice.next(sender,null,null,false).delivery().id();
  long attempt=mocks.prepare(sender,"rollback").attempt().id();mocks.open(sender,attempt,0,false);
  new TransactionTemplate(transactions).executeWithoutResult(status->{
   practice.answer(sender,delivery,0);mocks.answer(sender,attempt,0,0,0);status.setRollbackOnly();
  });
  assertThat(practice.delivery(sender,delivery).answered()).isFalse();
  assertThat(mocks.open(sender,attempt,0,false).item().selected()).isNull();
  assertThat(grant().getPracticeUsed()).isZero();assertThat(grant().getMocksUsed()).isZero();
 }
 @Test void competingLogicalQuestionsCannotExceedLastSlot() throws Exception {
  var g=grant();g.setPracticeLimit(1);grants.saveAndFlush(g);
  long a=practice.next(sender,null,null,false).delivery().id();
  long b=practice.next(sender,null,a,false).delivery().id();
  var outcomes=race(()->answerOutcome(a),()->answerOutcome(b));
  assertThat(outcomes).containsExactlyInAnyOrder("answered","practice.limit");
  assertThat(grant().getPracticeUsed()).isEqualTo(1);assertThat(progress.get(sender).answered()).isEqualTo(1);
 }
 String answerOutcome(long id) {
  try { practice.answer(sender,id,0);return "answered"; }
  catch(com.airlineprep.bot.common.ExamException e) { return e.key(); }
 }
}
