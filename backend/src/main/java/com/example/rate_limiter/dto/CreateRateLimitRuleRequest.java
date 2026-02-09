package com.example.rate_limiter.dto;

import com.example.rate_limiter.domain.RateLimitType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Value;

import java.util.UUID;

@Value
public class CreateRateLimitRuleRequest {
    @NotNull
    RateLimitType limitType;

    @Positive
    long limitValue;

    /** Required for WINDOW type: window duration in seconds (e.g. 60). */
    Integer windowSeconds;

    /** Required for GLOBAL with window: window in seconds. */
    Integer globalWindowSeconds;

    /** Required for WINDOW and MONTHLY; null for GLOBAL. */
    UUID clientId;
}
