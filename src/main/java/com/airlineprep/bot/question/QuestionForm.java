package com.airlineprep.bot.question;
import java.util.*;
public class QuestionForm extends QuestionContent {
 private List<String> options = new ArrayList<>(Collections.nCopies(8,""));
 private Integer correctOption;
 private Long expectedRevision;
 public List<String> getOptions() { return options; }
 public void setOptions(List<String> v) { options=v; }
 public Integer getCorrectOption() { return correctOption; }
 public void setCorrectOption(Integer v) { correctOption=v; }
 public Long getExpectedRevision() { return expectedRevision; }
 public void setExpectedRevision(Long v) { expectedRevision=v; }
 public static QuestionForm from(Question q) {
  QuestionForm f=new QuestionForm();
  org.springframework.beans.BeanUtils.copyProperties(q.currentVersion.content,f);
  for(int i=0;i<q.currentVersion.options.size();i++) {
   f.options.set(i,q.currentVersion.options.get(i).text);
   if(q.currentVersion.options.get(i).correct) f.correctOption=i;
  }
  f.expectedRevision=q.revision;
  return f;
 }
}
