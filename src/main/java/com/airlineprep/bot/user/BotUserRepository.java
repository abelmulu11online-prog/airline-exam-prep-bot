package com.airlineprep.bot.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {
    java.util.Optional<BotUser> findByTelegramUserId(long telegramUserId);
    java.util.Optional<BotUser> findByPhoneIdentityHash(String hash);
    long countByRegistrationStatus(RegistrationStatus status);
}
