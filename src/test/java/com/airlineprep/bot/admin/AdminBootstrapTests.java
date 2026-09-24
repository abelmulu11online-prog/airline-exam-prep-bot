package com.airlineprep.bot.admin;

import com.airlineprep.bot.IsolatedDatabaseSupport;
import com.airlineprep.bot.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @Transactional
class AdminBootstrapTests extends IsolatedDatabaseSupport {
    @Autowired AdminUserRepository admins;
    @Autowired SettingsService settings;
    @Autowired PasswordEncoder encoder;
    @Test void absentCredentialsNeverCreateDefaultAdmin() {
        new AdminBootstrap(admins,settings,encoder,"","").run(null);
        new AdminBootstrap(admins,settings,encoder,"operator","").run(null);
        assertThat(admins.count()).isZero();
    }
    @Test void initialAdminIsHashedAndNeverResetOnRestart() {
        new AdminBootstrap(admins,settings,encoder,"operator","test-only-strong-password").run(null);
        String hash = admins.findByUsername("operator").orElseThrow().getPasswordHash();
        assertThat(encoder.matches("test-only-strong-password",hash)).isTrue();
        new AdminBootstrap(admins,settings,encoder,"operator","different-test-password").run(null);
        assertThat(admins.findByUsername("operator").orElseThrow().getPasswordHash()).isEqualTo(hash);
    }
    @Test void weakBootstrapCredentialsFailSafely() {
        assertThatIllegalStateException().isThrownBy(() -> new AdminBootstrap(admins,settings,encoder,"operator","weak").run(null))
            .withMessageNotContaining("weak");
    }
}
