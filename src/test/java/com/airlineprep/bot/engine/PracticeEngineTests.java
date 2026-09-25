package com.airlineprep.bot.engine;
import com.airlineprep.bot.practice.*;
import com.airlineprep.bot.question.*;
import com.airlineprep.bot.common.ExamException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @Transactional
class PracticeEngineTests extends EngineFixture {
 @Autowired PracticeService practice;
 @Autowired StudentProgressService progress;
 @Autowired JdbcTemplate jdbc;
 @BeforeEach void setup() { prepareFixture(2); }
 @Test void showSkipAndDoubleNextCostNothing() {
  content(3);var first=practice.next(sender,null,null,false);
  var again=practice.next(sender,null,null,false);assertThat(again.delivery().id()).isEqualTo(first.delivery().id());
  var next=practice.next(sender,null,first.delivery().id(),false);
  assertThat(next.question().id()).isNotEqualTo(first.question().id());
  assertThat(practice.next(sender,null,first.delivery().id(),false).delivery().id()).isEqualTo(next.delivery().id());
  assertThat(grant().getPracticeUsed()).isZero();assertThat(progress.get(sender).answered()).isZero();
 }
 @Test void answerRetriesAndReviewUseFirstAnswerMetric() {
  content(1);var first=practice.next(sender,null,null,false);
  var incorrect=practice.answer(sender,first.delivery().id(),1);
  assertThat(incorrect.correct()).isFalse();
  assertThat(practice.answer(sender,first.delivery().id(),0).correct()).isFalse();
  var repeat=practice.next(sender,null,null,true);practice.answer(sender,repeat.delivery().id(),0);
  assertThat(grant().getPracticeUsed()).isEqualTo(1);
  var p=progress.get(sender);assertThat(p.answered()).isEqualTo(1);assertThat(p.correct()).isZero();assertThat(p.percentage()).isZero();
  assertThat(p.categories().getFirst().incorrect()).isEqualTo(1);
 }
 @Test void revisionAndArchivePreserveDeliveredVersionAndQuotaIdentity() {
  long q=question("Fictional original",true,false,true);
  var first=practice.next(sender,null,null,false);long version=first.question().versionId();
  var edit=questions.form(q);edit.setCorrectOption(1);edit.setQuestionText("Fictional corrected");questions.save(q,edit,"test-admin");publish(q);
  practice.answer(sender,first.delivery().id(),0);
  assertThat(practice.delivery(sender,first.delivery().id()).correct()).isTrue();
  var revised=practice.next(sender,null,null,true);
  assertThat(revised.question().versionId()).isNotEqualTo(version);
  questions.transition(q,QuestionStatus.ARCHIVED,questions.get(q).getRevision(),"test-admin");
  entityManager.flush();
  assertThat(practice.answer(sender,revised.delivery().id(),1).correct()).isTrue();
  assertThat(grant().getPracticeUsed()).isEqualTo(1);
  assertThatThrownBy(()->practice.next(sender,null,null,true)).isInstanceOf(ExamException.class).hasMessage("practice.empty");
 }
 @Test void defaultHundredUniqueLimitAndRepeatAtLimit() {
  content(101);Long prior=null;long first=0;
  for(int i=0;i<100;i++) {
   var v=practice.next(sender,null,prior,false);if(i==0) first=v.delivery().id();
   practice.answer(sender,v.delivery().id(),i%2);prior=v.delivery().id();
  }
  assertThat(grant().getPracticeUsed()).isEqualTo(100);
  final Long previous=prior;
  assertThatThrownBy(()->practice.next(sender,null,previous,false)).hasMessage("practice.limit");
  practice.answer(sender,first,0);
  var review=practice.next(sender,null,null,true);practice.answer(sender,review.delivery().id(),0);
  assertThat(grant().getPracticeUsed()).isEqualTo(100);assertThat(grant().getMocksUsed()).isZero();
  assertThat(progress.get(sender).answered()).isEqualTo(100);assertThat(progress.get(sender).percentage()).isEqualTo(50);
 }
 @Test void limitRecheckedForAnAlreadyDeliveredNewQuestion() {
  content(2);var a=practice.next(sender,null,null,false);var b=practice.next(sender,null,a.delivery().id(),false);
  grant().setPracticeLimit(1);
  practice.answer(sender,a.delivery().id(),0);
  assertThatThrownBy(()->practice.answer(sender,b.delivery().id(),0)).hasMessage("practice.limit");
  assertThat(practice.delivery(sender,b.delivery().id()).answered()).isFalse();
  var review=practice.next(sender,null,null,true);
  assertThat(review.question().id()).isEqualTo(a.question().id());
  practice.answer(sender,review.delivery().id(),0);
  var linked=practice.next(sender,null,a.delivery().id(),true);
  assertThat(linked.question().id()).isEqualTo(a.question().id());
  assertThat(practice.next(sender,null,a.delivery().id(),true).delivery().id()).isEqualTo(linked.delivery().id());
 }
 @Test void freePoolExamCategoryAndLifecycleAreEnforced() {
  question("Premium only",false,true,true);
  assertThat(practice.categories(sender)).isEmpty();
  assertThatThrownBy(()->practice.next(sender,null,null,false)).hasMessage("practice.empty");
  var draft=questions.save(null,form("Draft",true,false,true),"test-admin");
  assertThatThrownBy(()->practice.next(sender,null,null,false)).hasMessage("practice.empty");
  questions.transition(draft,QuestionStatus.REVIEWED,questions.get(draft).getRevision(),"test-admin");
  assertThatThrownBy(()->practice.next(sender,null,null,false)).hasMessage("practice.empty");
  assertThatThrownBy(()->practice.next(sender,999999L,null,false)).hasMessage("student.invalid");
 }
 @Test void lifetimeCanUsePremiumAndRepeatWithoutLimits() {
  question("Premium only",false,true,true);grant().setAccessLevel("LIFETIME");grant().setPracticeLimit(0);
  var first=practice.next(sender,null,null,false);practice.answer(sender,first.delivery().id(),0);
  var repeat=practice.next(sender,null,first.delivery().id(),false);practice.answer(sender,repeat.delivery().id(),1);
  assertThat(grant().getPracticeUsed()).isZero();assertThat(progress.get(sender).answered()).isEqualTo(1);
 }
 @Test void invalidOptionAndUnregisteredSenderCannotMutate() {
  content(1);var v=practice.next(sender,null,null,false);
  assertThatThrownBy(()->practice.answer(sender,v.delivery().id(),8)).hasMessage("student.invalid");
  assertThatThrownBy(()->practice.answer(sender+9999,v.delivery().id(),0)).hasMessage("student.register");
  assertThat(grant().getPracticeUsed()).isZero();
 }
 @Test void anotherRegisteredUserCannotReadAnswerOrAdvanceDelivery() {
  content(1);var v=practice.next(sender,null,null,false);long firstSender=sender;
  prepareFixture(2);
  assertThatThrownBy(()->practice.delivery(sender,v.delivery().id())).hasMessage("student.invalid");
  assertThatThrownBy(()->practice.answer(sender,v.delivery().id(),0)).hasMessage("student.invalid");
  assertThatThrownBy(()->practice.next(sender,null,v.delivery().id(),false)).hasMessage("student.invalid");
  assertThat(practice.delivery(firstSender,v.delivery().id()).answered()).isFalse();
 }
 @Test void zeroHistoryAndContentShortageAreNotQuotaExhaustion() {
  assertThat(progress.get(sender).percentage()).isZero();
  assertThatThrownBy(()->practice.next(sender,null,null,false)).hasMessage("practice.empty");
  content(1);var v=practice.next(sender,null,null,false);practice.answer(sender,v.delivery().id(),0);
  assertThatThrownBy(()->practice.next(sender,null,v.delivery().id(),false)).hasMessage("practice.empty");
  assertThat(grant().getPracticeUsed()).isEqualTo(1);
 }
 @Test void categoryAndExamIsolationAndInactiveContent() {
  long originalExam=exam,originalCategory=category;
  long other=catalog.save(true,null,new com.airlineprep.bot.admin.CatalogForm("other","Other","",true,0,exam),"test-admin");
  category=other;question("Other category",true,false,true);category=originalCategory;
  question("Chosen category",true,false,true);
  assertThat(practice.next(sender,originalCategory,null,false).question().categoryId()).isEqualTo(originalCategory);
  long firstSender=sender;prepareFixture(2);content(1);
  assertThatThrownBy(()->practice.next(firstSender,category,null,false)).hasMessage("student.invalid");
  assertThat(practice.next(sender,null,null,false).question().examId()).isEqualTo(exam).isNotEqualTo(originalExam);
  catalog.save(true,category,new com.airlineprep.bot.admin.CatalogForm("numbers","Fictional numbers","",false,0,exam),"test-admin");
  entityManager.flush();
  assertThat(practice.categories(sender)).isEmpty();
 }
 @Test void firstAnswerAccuracyRoundsAndCategoryPagesRemainBounded() {
  content(3);Long previous=null;
  for(int i=0;i<3;i++) {var p=practice.next(sender,null,previous,false);practice.answer(sender,p.delivery().id(),i==0?0:1);previous=p.delivery().id();}
  assertThat(progress.get(sender).percentage()).isEqualTo(33.33);
  for(int i=0;i<21;i++) {
   category=catalog.save(true,null,new com.airlineprep.bot.admin.CatalogForm("page"+i,"Page "+i,"",true,i,exam),"test-admin");
   question("Page question "+i,true,false,true);
  }
  assertThat(practice.categories(sender,0)).hasSize(21);
  assertThat(practice.categories(sender,1)).hasSize(2);
  assertThatThrownBy(()->practice.categories(sender,-1)).hasMessage("student.invalid");
 }
}
