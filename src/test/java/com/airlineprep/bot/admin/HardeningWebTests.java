package com.airlineprep.bot.admin;
import com.airlineprep.bot.IsolatedDatabaseSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest @AutoConfigureMockMvc @Transactional
class HardeningWebTests extends IsolatedDatabaseSupport {
 @Autowired MockMvc mvc;@Autowired AdminUserRepository admins;@Autowired PasswordEncoder encoder;
 @ParameterizedTest @ValueSource(strings={"/admin/settings","/admin/exam-types/1/edit","/admin/categories/1/edit","/admin/questions/1","/admin/questions/import/1","/admin/payments/1","/admin/payments/1/receipt","/admin/payment-methods/1/edit","/admin/payment-audit"})
 void everySensitiveReadRequiresWebAdmin(String path) throws Exception {
  mvc.perform(get(path)).andExpect(status().is3xxRedirection());
  mvc.perform(get(path).with(user("telegram-user").roles("USER"))).andExpect(status().isForbidden());
 }
 @ParameterizedTest @ValueSource(strings={"/admin/settings","/admin/exam-types/new","/admin/exam-types/1/edit","/admin/exam-types/1/toggle","/admin/categories/new","/admin/categories/1/edit","/admin/categories/1/toggle","/admin/questions/new","/admin/questions/1/edit","/admin/questions/1/transition","/admin/questions/import","/admin/questions/import/1/confirm","/admin/questions/import/1/cancel","/admin/payment-methods/new","/admin/payment-methods/1/edit","/admin/payments/1/approve","/admin/payments/1/reject","/admin/payments/1/retry-notification","/admin/logout"})
 void allMutationsRequireCsrfAndAuthentication(String path) throws Exception {
  mvc.perform(post(path).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
  if(!path.endsWith("logout")) mvc.perform(post(path).with(csrf())).andExpect(status().is3xxRedirection());
 }
 @ParameterizedTest @ValueSource(strings={"env","beans","configprops","heapdump","threaddump","mappings","loggers","shutdown"})
 void managementInternalsStayPrivate(String endpoint) throws Exception {mvc.perform(get("/actuator/"+endpoint)).andExpect(status().isForbidden());}
 @Test void loginRotatesSessionLogoutInvalidatesAndErrorsStayGeneric() throws Exception {
  var admin=new AdminUser();admin.setUsername("phase8-admin");admin.setPasswordHash(encoder.encode("fictional-test-password-only"));admin.setEnabled(true);admins.saveAndFlush(admin);
  var session=new MockHttpSession();String old=session.getId();
  var result=mvc.perform(post("/admin/login").session(session).with(csrf()).param("username","phase8-admin").param("password","fictional-test-password-only"))
   .andExpect(redirectedUrl("/admin")).andReturn();
  assertThat(result.getRequest().getSession().getId()).isNotEqualTo(old);
  mvc.perform(get("/admin").session(session)).andExpect(status().isOk());
  mvc.perform(post("/admin/logout").session(session).with(csrf())).andExpect(redirectedUrl("/admin/login?logout"));assertThat(session.isInvalid()).isTrue();
 }
 @Test void browserHeadersEmptyStatesAndSafeErrors() throws Exception {
  for(String path:new String[]{"/admin","/admin/settings","/admin/questions","/admin/questions/import","/admin/payment-methods","/admin/payments","/admin/payment-audit"})
   mvc.perform(get(path).with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
    .andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("X-Frame-Options","DENY"))
    .andExpect(header().string("Referrer-Policy","no-referrer")).andExpect(header().string("Content-Security-Policy",org.hamcrest.Matchers.containsString("script-src 'none'")));
  mvc.perform(get("/admin/questions/99999999").with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound()).andExpect(content().string(org.hamcrest.Matchers.containsString("could not be found")));
  mvc.perform(get("/admin/payments").param("status","' OR 1=1 --").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("No payments match")));
 }
 @Test void safeErrorTranslationNeverReflectsExceptionDetails() {
  var result=new SafeWebErrors().error(new org.springframework.dao.DataAccessResourceFailureException("SQLState password private-path"));
  assertThat(result.getStatus().value()).isEqualTo(503);assertThat(result.getModel().toString()).doesNotContain("password","SQLState","private-path");
 }
}
