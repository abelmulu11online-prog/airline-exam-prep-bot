package com.airlineprep.bot.payment;
import java.time.*;
import java.util.*;
import com.airlineprep.bot.common.ExamStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class PaymentAuditService {
 private final ExamStore store;private final Clock clock;
 public PaymentAuditService(ExamStore s,Clock c) {store=s;clock=c;}
 @Transactional(propagation=Propagation.MANDATORY)
 public void append(String actorType,String actor,String action,String entity,long id,String metadata) {
  store.insert("payment_audit_events",ExamStore.values("actor_type",actorType,"actor",actor,"action",action,
   "entity_type",entity,"entity_id",id,"metadata",metadata,"created_at",clock.instant()));
 }
 public record Event(long id,String actorType,String actor,String action,String entityType,long entityId,String metadata,Instant created) {}
 @Transactional(readOnly=true)
 public List<Event> forPayment(long id) {
  return store.jdbc().query("SELECT * FROM payment_audit_events WHERE entity_type='PAYMENT' AND entity_id=? ORDER BY id DESC LIMIT 25",
   (r,n)->new Event(r.getLong("id"),r.getString("actor_type"),r.getString("actor"),r.getString("action"),
    r.getString("entity_type"),r.getLong("entity_id"),r.getString("metadata"),ExamStore.instant(r,"created_at")),id);
 }
 @Transactional(readOnly=true)
 public List<Event> list(String action,String entity,String actorType,LocalDate date,int page) {
  var args=new ArrayList<Object>();String sql="SELECT * FROM payment_audit_events WHERE 1=1";
  if(action!=null&&!action.isBlank()) {sql+=" AND action=?";args.add(action);}
  if(entity!=null&&!entity.isBlank()) {sql+=" AND entity_type=?";args.add(entity);}
  if(actorType!=null&&!actorType.isBlank()) {sql+=" AND actor_type=?";args.add(actorType);}
  if(date!=null) {sql+=" AND created_at>=? AND created_at<?";args.add(java.sql.Timestamp.from(date.atStartOfDay(ZoneOffset.UTC).toInstant()));args.add(java.sql.Timestamp.from(date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));}
  args.add(Math.min(Math.max(page,0),1000000)*25);
  return store.jdbc().query(sql+" ORDER BY id DESC LIMIT 25 OFFSET ?",(r,n)->new Event(r.getLong("id"),r.getString("actor_type"),
   r.getString("actor"),r.getString("action"),r.getString("entity_type"),r.getLong("entity_id"),r.getString("metadata"),ExamStore.instant(r,"created_at")),args.toArray());
 }
}
