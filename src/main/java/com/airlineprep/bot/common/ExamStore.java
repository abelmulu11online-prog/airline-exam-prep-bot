package com.airlineprep.bot.common;
import java.time.Instant;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
@Repository
public class ExamStore {
 private final JdbcTemplate jdbc;
 private final Map<String,SimpleJdbcInsert> inserts=new ConcurrentHashMap<>();
 public ExamStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
 public JdbcTemplate jdbc() { return jdbc; }
 public long insert(String table,Map<String,Object> values) {
  return inserts.computeIfAbsent(table,t->new SimpleJdbcInsert(jdbc).withTableName(t).usingGeneratedKeyColumns("id"))
   .executeAndReturnKey(values).longValue();
 }
 public static Map<String,Object> values(Object... pairs) {
  Map<String,Object> result=new LinkedHashMap<>();
  for(int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1] instanceof Instant t?Timestamp.from(t):pairs[i+1]);
  return result;
 }
 public static Instant instant(ResultSet r,String column) throws SQLException {
  Timestamp value=r.getTimestamp(column);return value==null?null:value.toInstant();
 }
}
