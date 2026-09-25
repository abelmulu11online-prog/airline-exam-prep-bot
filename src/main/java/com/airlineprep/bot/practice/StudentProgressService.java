package com.airlineprep.bot.practice;
import java.util.*;
import com.airlineprep.bot.access.StudentAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional
public class StudentProgressService {
 private final StudentAccess access;private final JdbcTemplate jdbc;
 public StudentProgressService(StudentAccess a,JdbcTemplate j) { access=a;jdbc=j; }
 public record Category(long id,String name,long answered,long correct) {
  public long incorrect() { return answered-correct; }
  public double percentage() { return answered==0?0:Math.round(correct*10000.0/answered)/100.0; }
 }
 public record MockHistory(long id,String status,int total,int correct) {
  public double percentage() { return Math.round(correct*10000.0/total)/100.0; }
 }
 public record Progress(StudentAccess.Student student,long answered,long correct,List<Category> categories,long completed,List<MockHistory> recent) {
  public long incorrect() { return answered-correct; }
  public double percentage() { return answered==0?0:Math.round(correct*10000.0/answered)/100.0; }
 }
 public Progress get(long sender) {
  var s=access.lock(sender);
  var categories=jdbc.query("""
   SELECT v.category_id,v.category_name,COUNT(*),SUM(CASE WHEN o.correct=true THEN 1 ELSE 0 END)
   FROM practice_usage u JOIN practice_deliveries d ON d.id=u.first_delivery_id
   JOIN question_versions v ON v.id=d.version_id
   JOIN question_options o ON o.version_id=d.version_id AND o.position=d.selected_option
   WHERE u.user_id=? AND v.exam_type_id=?
   GROUP BY v.category_id,v.category_name ORDER BY v.category_id,v.category_name
   """,(r,n)->new Category(r.getLong(1),r.getString(2),r.getLong(3),r.getLong(4)),s.id(),s.examId());
  long total=categories.stream().mapToLong(Category::answered).sum(),correct=categories.stream().mapToLong(Category::correct).sum();
  long completed=jdbc.queryForObject("SELECT COUNT(*) FROM mock_attempts WHERE user_id=? AND exam_type_id=? AND status IN ('SUBMITTED','EXPIRED') AND first_answer_at IS NOT NULL",Long.class,s.id(),s.examId());
  var recent=jdbc.query("SELECT id,status,question_count,correct_count FROM mock_attempts WHERE user_id=? AND exam_type_id=? AND status IN ('SUBMITTED','EXPIRED') AND first_answer_at IS NOT NULL ORDER BY id DESC LIMIT 10",
   (r,n)->new MockHistory(r.getLong(1),r.getString(2),r.getInt(3),r.getInt(4)),s.id(),s.examId());
  return new Progress(s,total,correct,categories,completed,recent);
 }
}
