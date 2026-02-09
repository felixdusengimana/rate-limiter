package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.RateLimitType;

import java.util.UUID;

/**
 * Describes a single rate limit to enforce (from subscription plan or global rule).
 * Used by {@link DistributedRateLimitService} so both plan-based and rule-based limits use the same logic.
 */
public record EffectiveLimit(
    RateLimitType limitType,
    long limitValue,
    Integer windowSeconds,
    UUID clientId,
    Integer globalWindowSeconds
) {
    public static EffectiveLimit fromPlanMonthly(UUID clientId, long monthlyLimit) {
        return new EffectiveLimit(RateLimitType.MONTHLY, monthlyLimit, null, clientId, null);
    }

    public static EffectiveLimit fromPlanWindow(UUID clientId, long windowLimit, int windowSeconds) {
        return new EffectiveLimit(RateLimitType.WINDOW, windowLimit, windowSeconds, clientId, null);
    }

    public static EffectiveLimit fromGlobalRule(long limitValue, Integer globalWindowSeconds) {
        return new EffectiveLimit(
            globalWindowSeconds != null && globalWindowSeconds > 0 ? RateLimitType.GLOBAL : RateLimitType.GLOBAL,
            limitValue,
            null,
            null,
            globalWindowSeconds
        );
    }
}
