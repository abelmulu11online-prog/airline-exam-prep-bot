package com.airlineprep.bot.payment;
import java.time.*;
import java.util.*;
import com.airlineprep.bot.common.ExamStore;
import com.airlineprep.bot.settings.SettingsService;
import com.airlineprep.bot.telegram.TelegramBotClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
@Component @EnableScheduling
public class PaymentNotificationDispatcher {
 private final ExamStore store;private final SettingsService settings;private final PaymentQueries payments;
 private final PaymentAuditService audit;private final ObjectProvider<TelegramBotClient> clients;private final MessageSource messages;
 private final TransactionTemplate tx;private final Clock clock;
 @org.springframework.beans.factory.annotation.Value("${payment.notifications.automatic:true}")
 private boolean automatic;
 public PaymentNotificationDispatcher(ExamStore s,SettingsService settings,PaymentQueries p,PaymentAuditService a,
  ObjectProvider<TelegramBotClient> c,MessageSource m,PlatformTransactionManager manager,Clock clock) {
  store=s;this.settings=settings;payments=p;audit=a;clients=c;messages=m;this.clock=clock;
  tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
 }
 record Work(long id,long request,String kind,long recipient,String language,int attempts,String token) {}
 @Scheduled(fixedDelay=10000,initialDelay=10000)
 public void scheduled() {
  if(automatic) try {retryDue();} catch(RuntimeException error) {
   org.slf4j.LoggerFactory.getLogger(getClass()).warn("Notification storage unavailable; next scheduled pass will retry; details withheld");
  }
 }
 public void retryDue() {
  if(clients.getIfAvailable()==null) return;
  tx.executeWithoutResult(status->{
   settings.lock();
   var exhausted=store.jdbc().query("SELECT id,request_id,kind FROM payment_notifications WHERE status='SENDING' AND attempts>=5 AND next_attempt_at<=? ORDER BY id LIMIT 10",
    (r,n)->new Object[]{r.getLong(1),r.getLong(2),r.getString(3)},java.sql.Timestamp.from(clock.instant()));
   for(var row:exhausted) {
    store.jdbc().update("UPDATE payment_notifications SET status='FAILED',claim_token=NULL WHERE id=?",row[0]);
    audit.append("SYSTEM","notification",(row[2].equals("ADMIN_PENDING")?"ADMIN":"USER")+"_NOTIFICATION_FAILED","PAYMENT",(Long)row[1],"lease-expired; manual-retry-available");
   }
  });
  var ids=store.jdbc().query("SELECT id FROM payment_notifications WHERE status IN ('NEW','FAILED','SENDING') AND attempts<5 AND next_attempt_at<=? ORDER BY id LIMIT 10",
   (r,n)->r.getLong(1),java.sql.Timestamp.from(clock.instant()));
  for(long id:ids) {if(Thread.currentThread().isInterrupted()) break;deliver(id);}
 }
 public void deliver(long id) {
  var client=clients.getIfAvailable();if(client==null) return;
  Work work=tx.execute(status->{
   settings.lock();
   var rows=store.jdbc().query("SELECT * FROM payment_notifications WHERE id=? AND status IN ('NEW','FAILED','SENDING') AND attempts<5 AND next_attempt_at<=?",
    (r,n)->new Work(r.getLong("id"),r.getLong("request_id"),r.getString("kind"),r.getLong("recipient_id"),r.getString("language"),r.getInt("attempts")+1,UUID.randomUUID().toString()),id,java.sql.Timestamp.from(clock.instant()));
   if(rows.isEmpty()) return null;
   store.jdbc().update("UPDATE payment_notifications SET status='SENDING',attempts=attempts+1,next_attempt_at=?,claim_token=? WHERE id=?",
    java.sql.Timestamp.from(clock.instant().plusSeconds(120)),rows.getFirst().token(),id);return rows.getFirst();
  });
  if(work==null) return;
  boolean sent=false;long retryDelay=60L*work.attempts();
  try {
   var p=payments.get(work.request());String text;
   if(work.kind().equals("ADMIN_PENDING")) text="New payment waiting for review. Request #"+p.id()+"; "+p.amount().toPlainString()+" "+p.currency()+"; "+p.methodName()+"; submitted "+p.submitted()+". Review in the secured web admin.";
   else {
    var locale=Locale.forLanguageTag(work.language());
    text=messages.getMessage(work.kind().equals("USER_APPROVED")?"payment.approved":"payment.rejected",
     new Object[]{p.rejectionReason()==null?"":p.rejectionReason()},locale);
   }
   Map<String,?> markup=work.kind().equals("ADMIN_PENDING")?Map.of():Map.of("inline_keyboard",List.of(
    List.of(Map.of("text",messages.getMessage("student.menu",null,Locale.forLanguageTag(work.language())),"callback_data","s:home"))));
   client.sendMessage(work.recipient(),text,markup);sent=true;
  } catch(InterruptedException e) {Thread.currentThread().interrupt();}
  catch(TelegramBotClient.ApiException e) {
   retryDelay=Math.max(retryDelay,Math.min(e.retryAfterSeconds(),3600));
   org.slf4j.LoggerFactory.getLogger(getClass()).warn("Payment notification failed (code {}); retry retained",e.code());
  }
  catch(RuntimeException e) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("Payment notification failed; retry retained; details withheld");}
  final boolean delivered=sent;
  final long delay=retryDelay;
  tx.executeWithoutResult(status->{
   settings.lock();
   int changed=store.jdbc().update("UPDATE payment_notifications SET status=?,sent_at=?,next_attempt_at=?,claim_token=NULL WHERE id=? AND status='SENDING' AND claim_token=?",
    delivered?"SENT":"FAILED",delivered?java.sql.Timestamp.from(clock.instant()):null,
    java.sql.Timestamp.from(clock.instant().plusSeconds(delay)),id,work.token());
   if(changed==1) audit.append("SYSTEM","notification",(work.kind().equals("ADMIN_PENDING")?"ADMIN":"USER")+"_NOTIFICATION_"+(delivered?"SENT":"FAILED"),"PAYMENT",work.request(),"attempt="+work.attempts());
  });
 }
 public void retry(long request,String admin) {
  tx.executeWithoutResult(status->{
   settings.lock();payments.get(request);
   int changed=store.jdbc().update("UPDATE payment_notifications SET status='NEW',attempts=0,next_attempt_at=?,claim_token=NULL WHERE request_id=? AND status='FAILED'",
    java.sql.Timestamp.from(clock.instant()),request);
   if(changed>0) audit.append("WEB_ADMIN",admin,"NOTIFICATION_RETRY_REQUESTED","PAYMENT",request,"");
  });
 }
}
