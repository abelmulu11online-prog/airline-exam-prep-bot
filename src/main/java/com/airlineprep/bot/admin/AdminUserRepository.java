package com.airlineprep.bot.admin;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    java.util.Optional<AdminUser> findByUsername(String username);
}
