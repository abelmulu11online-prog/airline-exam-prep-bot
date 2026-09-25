package com.airlineprep.bot.question;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.airlineprep.bot.IsolatedDatabaseSupport;
import com.airlineprep.bot.admin.*;
import com.airlineprep.bot.audit.AdminChangeService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest
class QuestionTransactionTests extends IsolatedDatabaseSupport {
 @Autowired QuestionService questions;
 @Autowired QuestionImportService imports;
 @Autowired QuestionRepository repository;
 @Autowired CatalogService catalog;
 @Autowired JdbcTemplate jdbc;
 @MockitoSpyBean AdminChangeService changes;
 static final AtomicInteger IDS=new AtomicInteger();
 long exam,category;String examCode;
 @BeforeEach void setup() {
  examCode="tx-"+IDS.incrementAndGet();
  exam=catalog.save(false,null,new CatalogForm(examCode,"Fictional transaction exam","",true,0,null),"test-admin");
  category=catalog.save(true,null,new CatalogForm("numbers","Numbers","",true,0,exam),"test-admin");
 }
 QuestionFileParser.Parsed batch(String... prompts) {
  List<QuestionFileParser.Row> rows=new ArrayList<>();
  for(int i=0;i<prompts.length;i++) {
   Map<String,String> m=new LinkedHashMap<>();QuestionFileParser.HEADERS.forEach(h->m.put(h,""));
   m.put("exam_type",examCode);m.put("category","numbers");m.put("question",prompts[i]);
   m.put("option_a","One");m.put("option_b","Two");m.put("correct_answer","B");
   m.put("explanation","Fictional fixture");m.put("difficulty","EASY");m.put("source_type","Original");m.put("source","Fictional");
   m.put("copyright_status","ORIGINAL");m.put("free_available","true");m.put("premium_available","false");m.put("mock_available","false");
   rows.add(new QuestionFileParser.Row(i+2,m,""));
  }
  return new QuestionFileParser.Parsed("fictional.csv","CSV",rows);
 }
 @Test void concurrentConfirmationsCreateOnlyOnce() throws Exception {
  long id=imports.stage(batch("Fictional concurrent"),"test-admin");
  long before=repository.count();
  ExecutorService workers=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
  try {
   Callable<Void> confirm=()->{start.await();imports.confirm(id,"test-admin");return null;};
   var a=workers.submit(confirm);var b=workers.submit(confirm);start.countDown();a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
   assertThat(repository.count()).isEqualTo(before+1);
   assertThat(imports.get(id).importedRows).isEqualTo(1);
  } finally { workers.shutdownNow(); }
 }
 @Test void concurrentSeparateBatchesRecheckDuplicates() throws Exception {
  long a=imports.stage(batch("Fictional cross-batch"),"test-admin"),b=imports.stage(batch("Fictional cross-batch"),"test-admin");
  long before=repository.count();
  ExecutorService workers=Executors.newFixedThreadPool(2);
  try {
   var first=workers.submit(()->imports.confirm(a,"test-admin"));
   var second=workers.submit(()->imports.confirm(b,"test-admin"));
   first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);
   assertThat(repository.count()).isEqualTo(before+1);
   assertThat(imports.get(a).duplicateRows+imports.get(b).duplicateRows).isEqualTo(1);
  } finally { workers.shutdownNow(); }
 }

 @Test void unexpectedFailureRollsBackWholeConfirmationAndCanRetry() {
  long id=imports.stage(batch("Fictional rollback first","Fictional rollback second"),"test-admin");
  long before=repository.count();
  long versions=jdbc.queryForObject("select count(*) from question_versions",Long.class);
  long options=jdbc.queryForObject("select count(*) from question_options",Long.class);
  AtomicInteger calls=new AtomicInteger();
  AdminChangeService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(changes);
  doAnswer(invocation->{
   if(calls.incrementAndGet()==2) throw new IllegalStateException("Injected test failure");
   return invocation.callRealMethod();
  }).when(target).record(anyString(),eq("QUESTION_SAVED"),anyString(),anyString(),anyString());
  assertThatThrownBy(()->imports.confirm(id,"test-admin")).hasMessageContaining("Injected");
  assertThat(repository.count()).isEqualTo(before);
  assertThat(jdbc.queryForObject("select count(*) from question_versions",Long.class)).isEqualTo(versions);
  assertThat(jdbc.queryForObject("select count(*) from question_options",Long.class)).isEqualTo(options);
  assertThat(imports.get(id).importedRows).isZero();
  assertThat(imports.get(id).status).isEqualTo(ImportStatus.VALIDATED);
  reset(target);imports.confirm(id,"test-admin");
  assertThat(imports.get(id).importedRows).isEqualTo(2);
 }

}
