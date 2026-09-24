package com.airlineprep.bot.user;

import com.airlineprep.bot.settings.SettingsService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PhoneKeyGuard implements ApplicationRunner {
    private final PhoneIdentity identity;
    private final SettingsService settings;
    public PhoneKeyGuard(PhoneIdentity identity, SettingsService settings) {
        this.identity = identity; this.settings = settings;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (!identity.configured()) return;
        var configuration = settings.lock();
        String expected = configuration.getPhoneKeyFingerprint();
        if (expected != null && !expected.equals(identity.fingerprint())) {
            throw new IllegalStateException("Phone identity key changed; restore the original key before registration");
        }
        configuration.setPhoneKeyFingerprint(identity.fingerprint());
    }
}
