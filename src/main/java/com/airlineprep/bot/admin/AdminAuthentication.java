package com.airlineprep.bot.admin;

import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthentication implements UserDetailsService {
    private final AdminUserRepository admins;
    public AdminAuthentication(AdminUserRepository admins) { this.admins = admins; }
    @Override @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        AdminUser admin = admins.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return User.withUsername(admin.getUsername()).password(admin.getPasswordHash())
            .roles("ADMIN").disabled(!admin.getEnabled()).build();
    }
}
