package com.airlineprep.bot.access;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessEntitlementRepository extends JpaRepository<AccessEntitlement, Long> {
    java.util.Optional<AccessEntitlement> findByUserId(Long userId);
}
