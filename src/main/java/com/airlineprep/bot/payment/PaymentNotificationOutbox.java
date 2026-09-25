package com.airlineprep.bot.payment;
import java.time.Clock;
import com.airlineprep.bot.access.StudentAccess.Student;
import com.airlineprep.bot.common.ExamStore;
import com.airlineprep.bot.telegram.TelegramAdminProperties;
import com.airlineprep.bot.user.BotUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class PaymentNotificationOutbox {
 private final ExamStore store;private final TelegramAdminProperties admin;private final BotUserRepository users;
 private final PaymentAuditService audit;private final Clock clock;
 public PaymentNotificationOutbox(ExamStore s,TelegramAdminProperties a,BotUserRepository u,PaymentAuditService log,Clock c) {
  store=s;admin=a;users=u;audit=log;clock=c;
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public void enqueue(long request,String kind,Student student) {
  Long recipient=kind.equals("ADMIN_PENDING")?admin.id():users.findById(student.id()).orElseThrow().getTelegramUserId();
  store.insert("payment_notifications",ExamStore.values("request_id",request,"kind",kind,"recipient_id",recipient,
   "language",student.language(),"status",recipient==null?"SKIPPED":"NEW","attempts",0,"next_attempt_at",clock.instant(),"created_at",clock.instant()));
  if(recipient==null) audit.append("SYSTEM","notification","ADMIN_NOTIFICATION_SKIPPED","PAYMENT",request,"admin-not-configured");
 }
}
