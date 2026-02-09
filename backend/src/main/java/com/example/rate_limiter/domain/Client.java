package com.example.rate_limiter.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /**
     * API key used to identify the client in requests (e.g. X-API-Key header).
     */
    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    /**
     * Subscription plan determining this client's usage limits (monthly, and optional window).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    private SubscriptionPlan subscriptionPlan;

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
