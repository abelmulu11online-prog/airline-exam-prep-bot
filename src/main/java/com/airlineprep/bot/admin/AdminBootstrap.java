package com.airlineprep.bot.admin;

import java.nio.charset.StandardCharsets;
import com.airlineprep.bot.settings.SettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AdminUserRepository admins;
    private final SettingsService settings;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    public AdminBootstrap(AdminUserRepository admins, SettingsService settings, PasswordEncoder encoder,
            @Value("${admin.bootstrap.username:}") String username,
            @Value("${admin.bootstrap.password:}") String password) {
        this.admins = admins; this.settings = settings; this.encoder = encoder;
        this.username = username; this.password = password;
    }
    @Override @Transactional
    public void run(ApplicationArguments arguments) {
        settings.lock();
        if (admins.count() != 0 || username.isBlank() || password.isBlank()) return;
        if (!username.matches("[a-zA-Z0-9_.-]{3,64}") || password.length() < 16
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Admin bootstrap requires a valid username and a 16-72 byte strong password");
        }
        AdminUser admin = new AdminUser();
        admin.setUsername(username); admin.setPasswordHash(encoder.encode(password)); admin.setEnabled(true);
        admins.save(admin);
    }
}
