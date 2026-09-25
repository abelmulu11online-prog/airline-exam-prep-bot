package com.airlineprep.bot.mock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class MockScoringService {
 private final JdbcTemplate jdbc;
 public MockScoringService(JdbcTemplate j) { jdbc=j; }
 public record CategoryScore(long categoryId,String name,int total,int correct,int incorrect,int unanswered) {
  public double percentage() { return total==0?0:Math.round(correct*10000.0/total)/100.0; }
 }
 public record Score(int total,int correct,int incorrect,int unanswered,List<CategoryScore> categories) {
  public double percentage() { return total==0?0:Math.round(correct*10000.0/total)/100.0; }
 }
 public Score calculate(long attempt) {
  var categories=jdbc.query("""
   SELECT v.category_id,v.category_name,COUNT(*) total,
    SUM(CASE WHEN o.correct=true THEN 1 ELSE 0 END) correct,
    SUM(CASE WHEN i.selected_option IS NOT NULL AND o.correct=false THEN 1 ELSE 0 END) incorrect,
    SUM(CASE WHEN i.selected_option IS NULL THEN 1 ELSE 0 END) unanswered
   FROM mock_items i JOIN question_versions v ON v.id=i.version_id
   LEFT JOIN question_options o ON o.version_id=i.version_id AND o.position=i.selected_option
   WHERE i.attempt_id=? GROUP BY v.category_id,v.category_name ORDER BY v.category_id,v.category_name
   """,(r,n)->new CategoryScore(r.getLong(1),r.getString(2),r.getInt(3),r.getInt(4),r.getInt(5),r.getInt(6)),attempt);
  return new Score(categories.stream().mapToInt(CategoryScore::total).sum(),categories.stream().mapToInt(CategoryScore::correct).sum(),
   categories.stream().mapToInt(CategoryScore::incorrect).sum(),categories.stream().mapToInt(CategoryScore::unanswered).sum(),categories);
 }
}
