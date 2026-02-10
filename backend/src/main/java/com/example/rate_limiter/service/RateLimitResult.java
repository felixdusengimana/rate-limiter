package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.RateLimitType;
import com.example.rate_limiter.domain.ThrottleType;
import lombok.Builder;
import lombok.Value;

/**
 * Result of a rate limit check: allowed or denied, with metadata for headers.
 * - Client limits (WINDOW/MONTHLY): always hard throttle (immediate 429).
 * - Global limit: soft until 120% usage, then hard; 80%/100% trigger logging and TODO notify admin.
 */
@Value
@Builder(toBuilder = true)
public class RateLimitResult {
    boolean allowed;
    long limit;
    long current;
    long remaining;
    /** Seconds after which the client can retry (for 429 response). */
    long retryAfterSeconds;
    /** When denied: which limit type was exceeded. Null when allowed. */
    RateLimitType exceededByType;
    /** When a global limit was checked: current/limit (e.g. 0.85 = 85%). Used for 80%/100%/120% logging and soft vs hard. */
    Double globalUsageRatio;
    /** Throttle type: NONE (allowed), SOFT (encourage retry), HARD (reject immediately). */
    ThrottleType throttleType;
    /** Suggested delay in milliseconds for client to retry (only for SOFT throttling). */
    long softDelayMs;
}
