package com.airlineprep.bot.question;
import java.util.List;
public record StudentQuestion(long id,long versionId,long examId,long categoryId,String categoryName,
 String text,List<Option> options,String explanation) {
 public record Option(int position,String text,boolean correct) {}
 public Option option(int position) {
  return options.stream().filter(o->o.position()==position).findFirst()
   .orElseThrow(()->new com.airlineprep.bot.common.ExamException("student.invalid"));
 }
}
