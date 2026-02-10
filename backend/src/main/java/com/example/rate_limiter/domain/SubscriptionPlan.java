package com.example.rate_limiter.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Subscription plan defining monthly (and optional per-window) limits for clients.
 * Clients subscribe to a plan; rate limiting is applied based on their plan.
 */
@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Maximum requests per calendar month for this plan.
     */
    @Column(name = "monthly_limit", nullable = false)
    private long monthlyLimit;

    /**
     * Optional: max requests per time window (e.g. per minute). Null = no window cap.
     */
    @Column(name = "window_limit")
    private Long windowLimit;

    /**
     * When window_limit is set: window duration in seconds (e.g. 60 = 1 minute).
     */
    @Column(name = "window_seconds")
    private Integer windowSeconds;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void createdAt() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }

    /**
     * Check if this subscription is effectively active (both manually enabled AND not expired).
     * Requires:
     * 1. active = true (admin hasn't disabled it)
     * 2. expiresAt is null OR now is before expiresAt (not date-expired)
     * 
     * @return true only if BOTH conditions are met
     */
    @JsonIgnore
    public boolean isEffectivelyActive() {
        if (!active) {
            return false;
        }
        
        if (expiresAt == null) {
            return true;  // No expiry date = never expires
        }
        
        return Instant.now().isBefore(expiresAt);  // Still within validity window
    }
}
