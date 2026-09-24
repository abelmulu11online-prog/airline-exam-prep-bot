package com.airlineprep.bot.user;

import com.airlineprep.bot.IsolatedDatabaseSupport;
import com.airlineprep.bot.access.*;
import com.airlineprep.bot.examtype.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
class RegistrationRollbackTests extends IsolatedDatabaseSupport {
    @Autowired RegistrationService registration;
    @Autowired BotUserRepository users;
    @Autowired ExamTypeRepository exams;
    @MockitoSpyBean AccessEntitlementRepository entitlements;
    @Test void failedEntitlementInsertRollsBackCompletedIdentity() {
        ExamType exam = new ExamType();
        exam.setCode("rollback"); exam.setName("Rollback test"); exam.setNameAm(""); exam.setActive(true);
        exams.saveAndFlush(exam);
        registration.start(90001); registration.language(90001,"en"); registration.exam(90001,exam.getId());
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("simulated storage failure"))
            .when(entitlements).saveAndFlush(any(AccessEntitlement.class));
        assertThatThrownBy(() -> registration.contact(90001,90001L,"0912345678"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        var persisted = users.findByTelegramUserId(90001).orElseThrow();
        assertThat(persisted.getRegistrationStatus()).isEqualTo(RegistrationStatus.PHONE_REQUIRED);
        assertThat(persisted.getPhoneIdentityHash()).isNull();
        assertThat(persisted.getRegistrationCompletedAt()).isNull();
        assertThat(entitlements.findByUserId(persisted.getId())).isEmpty();
    }
}
