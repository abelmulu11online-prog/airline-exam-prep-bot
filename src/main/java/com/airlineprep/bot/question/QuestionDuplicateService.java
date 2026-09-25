package com.airlineprep.bot.question;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.Normalizer;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class QuestionDuplicateService {
 private final QuestionVersionRepository versions;
 public QuestionDuplicateService(QuestionVersionRepository v) { versions=v; }
 public record Result(DuplicateStatus status, String reason) {}
 public static String normalize(String value) {
  return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("(?U)\\s+"," ").strip();
 }
 public static String hash(String value) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
  catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
 }
 public static String fingerprint(QuestionForm f) {
  // Length prefixes avoid ambiguous boundaries; order/answer changes require human review.
  StringBuilder s=new StringBuilder();
  append(s,normalize(f.questionText));
  for(int i=0;i<f.getOptions().size();i++) if(!f.getOptions().get(i).isBlank()) {
   append(s,normalize(f.getOptions().get(i)));
   append(s,Objects.equals(i,f.getCorrectOption())?"correct":"incorrect");
  }
  return hash(s.toString());
 }
 private static void append(StringBuilder b,String s) { b.append(s.length()).append(':').append(s); }
 public static String stem(QuestionForm f) { return hash(normalize(f.questionText)); }
 public Result check(QuestionForm f) {
  var exact=versions.findFirstByContentExamTypeIdAndFingerprintOrderByIdAsc(f.examTypeId,fingerprint(f));
  if(exact.isPresent()) return new Result(DuplicateStatus.EXACT_DUPLICATE,"Same normalized question and answer options as question "+exact.get().questionId+".");
  var likely=versions.findFirstByContentExamTypeIdAndStemFingerprintOrderByIdAsc(f.examTypeId,stem(f));
  if(likely.isPresent()) return new Result(DuplicateStatus.LIKELY_DUPLICATE,"Same normalized question text as question "+likely.get().questionId+"; review differing options.");
  return new Result(DuplicateStatus.UNIQUE,"");
 }
}
