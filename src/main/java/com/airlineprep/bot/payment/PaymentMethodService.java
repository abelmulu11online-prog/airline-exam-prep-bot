package com.airlineprep.bot.payment;
import java.time.Clock;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.airlineprep.bot.common.*;
import com.airlineprep.bot.settings.SettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional @org.springframework.validation.annotation.Validated
public class PaymentMethodService {
 public record Form(@NotBlank @Pattern(regexp="TELEBIRR|BANK_TRANSFER") String type,
  @NotBlank @Size(max=100) String displayName,@NotBlank @Size(max=100) String accountName,
  @NotBlank @Size(max=150) String destination,@NotNull @Size(max=2000) String instructions,
  boolean active,@Min(0) int displayOrder,@Min(0) Long revision) {}
 public record Method(long id,Form details) {}
 private final ExamStore store;private final SettingsService settings;private final PaymentAuditService audit;private final Clock clock;
 public PaymentMethodService(ExamStore s,SettingsService settings,PaymentAuditService a,Clock c) {store=s;this.settings=settings;audit=a;clock=c;}
 public long save(Long id,@Valid Form f,String actor) {
  settings.lock();
  if(id==null) id=store.insert("payment_methods",ExamStore.values("type",f.type(),"display_name",f.displayName().strip(),
   "account_name",f.accountName().strip(),"destination",f.destination().strip(),"instructions",f.instructions().strip(),
   "active",f.active(),"display_order",f.displayOrder(),"revision",0,"created_at",clock.instant(),"updated_at",clock.instant()));
  else {
   var old=get(id);
   if(f.revision()==null||!f.revision().equals(old.details().revision())) throw new ExamException("payment.stale");
   store.jdbc().update("UPDATE payment_methods SET type=?,display_name=?,account_name=?,destination=?,instructions=?,active=?,display_order=?,revision=revision+1,updated_at=? WHERE id=?",
    f.type(),f.displayName().strip(),f.accountName().strip(),f.destination().strip(),f.instructions().strip(),f.active(),f.displayOrder(),java.sql.Timestamp.from(clock.instant()),id);
  }
  audit.append("WEB_ADMIN",actor,"PAYMENT_METHOD_SAVED","PAYMENT_METHOD",id,"type="+f.type()+"; active="+f.active());return id;
 }
 @Transactional(readOnly=true)
 public Method get(long id) {
  var found=store.jdbc().query("SELECT * FROM payment_methods WHERE id=?",(r,n)->read(r),id);
  if(found.isEmpty()) throw new ExamException("payment.methodInvalid");return found.getFirst();
 }
 @Transactional(readOnly=true)
 public List<Method> list(boolean active,int page) {
  return store.jdbc().query("SELECT * FROM payment_methods"+(active?" WHERE active=true":"")+" ORDER BY display_order,id LIMIT 20 OFFSET ?",
   (r,n)->read(r),Math.min(Math.max(page,0),1000000)*20);
 }
 private Method read(java.sql.ResultSet r) throws java.sql.SQLException {
  return new Method(r.getLong("id"),new Form(r.getString("type"),r.getString("display_name"),r.getString("account_name"),
   r.getString("destination"),r.getString("instructions"),r.getBoolean("active"),r.getInt("display_order"),r.getLong("revision")));
 }
}
