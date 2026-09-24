package com.airlineprep.bot.common;

import java.time.Instant;
import jakarta.persistence.*;

@MappedSuperclass
public abstract class TimedEntity {
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void created() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate
    protected void updated() { updatedAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
