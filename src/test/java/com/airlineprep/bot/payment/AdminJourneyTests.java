package com.airlineprep.bot.payment;
import java.util.*;
import java.nio.charset.StandardCharsets;
import com.airlineprep.bot.admin.*;
import com.airlineprep.bot.question.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest @AutoConfigureMockMvc @Transactional
class AdminJourneyTests extends PaymentFixture {
 @Autowired MockMvc mvc;@Autowired AdminUserRepository admins;@Autowired PasswordEncoder encoder;
 @Test void authenticatedAdminJourneyAndMassAssignmentProtection() throws Exception {
  setupPayment();var admin=new AdminUser();admin.setUsername("journey-admin");admin.setPasswordHash(encoder.encode("fictional-admin-password-only"));admin.setEnabled(true);admins.saveAndFlush(admin);
  var login=mvc.perform(post("/admin/login").with(csrf()).param("username","journey-admin").param("password","fictional-admin-password-only")).andExpect(status().is3xxRedirection()).andReturn();
  var session=(MockHttpSession)login.getRequest().getSession();
  for(String route:new String[]{"/admin","/admin/settings","/admin/exam-types","/admin/categories","/admin/questions","/admin/questions/import","/admin/payment-methods","/admin/payments","/admin/payment-audit"})mvc.perform(get(route).session(session)).andExpect(status().isOk());
  mvc.perform(post("/admin/settings").session(session).with(csrf()).param("freePracticeLimit","100").param("freeMockLimit","2").param("questionsPerMock","50").param("lifetimePrice","50.00").param("currency","ETB").param("paymentEnabled","true").param("manualPaymentEnabled","true").param("supportInfo","Fictional support")).andExpect(status().is3xxRedirection());
  mvc.perform(post("/admin/exam-types/new").session(session).with(csrf()).param("code","journey-admin").param("name","Fictional admin exam").param("nameAm","").param("active","true").param("displayOrder","0")).andExpect(status().is3xxRedirection());
  mvc.perform(post("/admin/categories/new").session(session).with(csrf()).param("examTypeId",Long.toString(exam)).param("code","admin-extra").param("name","Fictional category").param("nameAm","").param("active","true").param("displayOrder","0")).andExpect(status().is3xxRedirection());
  var created=mvc.perform(post("/admin/questions/new").session(session).with(csrf()).param("questionText","Fictional web question").param("status","PUBLISHED").param("createdBy","attacker").param("id","99999")).andExpect(status().is3xxRedirection()).andReturn();
  long question=Long.parseLong(created.getResponse().getRedirectedUrl().replace("/admin/questions/",""));assertThat(questions.get(question).getStatus()).isEqualTo(QuestionStatus.DRAFT);assertThat(questions.get(question).getCreatedBy()).isEqualTo("journey-admin");assertThat(question).isNotEqualTo(99999);
  String row=String.join(",",Collections.nCopies(QuestionFileParser.HEADERS.size(),"fictional"));byte[] data=(String.join(",",QuestionFileParser.HEADERS)+"\r\n"+row).getBytes(StandardCharsets.UTF_8);
  var upload=mvc.perform(multipart("/admin/questions/import").file(new MockMultipartFile("file","fictional.csv","text/csv",data)).session(session).with(csrf())).andExpect(status().is3xxRedirection()).andReturn();
  String batch=upload.getResponse().getRedirectedUrl();mvc.perform(get(batch).session(session)).andExpect(status().isOk());mvc.perform(post(batch+"/cancel").session(session).with(csrf())).andExpect(status().is3xxRedirection());
  mvc.perform(post("/admin/payment-methods/new").session(session).with(csrf()).param("type","TELEBIRR").param("displayName","Fictional").param("accountName","Fictional").param("destination","NOT-REAL").param("instructions","No transfer").param("displayOrder","0").param("_active","on")).andExpect(status().is3xxRedirection());
  long payment=pending();mvc.perform(post("/admin/payments/"+payment+"/approve").session(session).with(csrf()).param("userId","99999").param("amount","0").param("reviewedBy","attacker")).andExpect(status().is3xxRedirection());
  assertThat(queries.get(payment).reviewer()).isEqualTo("journey-admin");assertThat(queries.get(payment).amount()).isEqualByComparingTo("50");
  mvc.perform(get("/admin/payment-audit").session(session)).andExpect(status().isOk());mvc.perform(post("/admin/logout").session(session).with(csrf())).andExpect(status().is3xxRedirection());assertThat(session.isInvalid()).isTrue();
 }
}
