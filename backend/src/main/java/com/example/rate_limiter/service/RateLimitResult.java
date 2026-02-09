package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.RateLimitType;
import lombok.Builder;
import lombok.Value;

/**
 * Result of a rate limit check: allowed or denied, with metadata for headers.
 * When denied, exceededByType indicates which rule type caused it (GLOBAL → soft throttle, WINDOW/MONTHLY → hard).
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
    /** When denied: which limit type was exceeded (GLOBAL = soft throttling, WINDOW/MONTHLY = hard). Null when allowed. */
    RateLimitType exceededByType;
}
