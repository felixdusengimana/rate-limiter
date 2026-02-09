package com.example.rate_limiter.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rate_limit_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateLimitRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", nullable = false)
    private RateLimitType limitType;

    /**
     * Maximum number of requests allowed in the window or month.
     */
    @Column(name = "limit_value", nullable = false)
    private long limitValue;

    /**
     * For WINDOW type: window duration in seconds (e.g. 60 = 1 minute).
     * Ignored for MONTHLY and GLOBAL (monthly).
     */
    @Column(name = "window_seconds")
    private Integer windowSeconds;

    /**
     * For GLOBAL type with window: window duration in seconds. Null for monthly global.
     */
    @Column(name = "global_window_seconds")
    private Integer globalWindowSeconds;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void createdAt() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
