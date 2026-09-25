package com.airlineprep.bot.access;
import com.airlineprep.bot.user.*;
import com.airlineprep.bot.settings.SettingsService;
import com.airlineprep.bot.category.CategoryRepository;
import com.airlineprep.bot.common.ExamException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class StudentAccess {
 private final SettingsService settings;
 private final BotUserRepository users;
 private final AccessEntitlementRepository grants;
 private final CategoryRepository categories;
 public StudentAccess(SettingsService s,BotUserRepository u,AccessEntitlementRepository g,CategoryRepository c) {
  settings=s;users=u;grants=g;categories=c;
 }
 public record Student(long id,long examId,String language,AccessEntitlement grant) {
  public boolean lifetime() { return "LIFETIME".equals(grant.getAccessLevel()); }
  public int practiceRemaining() { return Math.max(0,grant.getPracticeLimit()-grant.getPracticeUsed()); }
  public int mockRemaining() { return Math.max(0,grant.getMockLimit()-grant.getMocksUsed()); }
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public Student lock(long telegramId) {
  // The existing singleton lock also serializes content publication and taxonomy changes.
  // No Telegram/network work occurs under this lock.
  settings.lock();
  BotUser user=users.findByTelegramUserId(telegramId).orElseThrow(()->new ExamException("student.register"));
  if(user.getRegistrationStatus()!=RegistrationStatus.COMPLETED) throw new ExamException("student.register");
  var grant=grants.findByUserId(user.getId()).orElseThrow(()->new ExamException("student.register"));
  return new Student(user.getId(),user.getSelectedExamTypeId(),user.getPreferredLanguage(),grant);
 }
 public void category(Student user,Long category) {
  if(category==null) return;
  var c=categories.findById(category).orElseThrow(()->new ExamException("student.invalid"));
  if(!c.getActive()||!c.getExamTypeId().equals(user.examId())) throw new ExamException("student.invalid");
 }
}
