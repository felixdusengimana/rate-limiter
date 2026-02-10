package com.example.rate_limiter.domain;

/**
 * Throttling strategy when a rate limit is exceeded:
 * - NONE: No throttling, request is allowed
 * - SOFT: Client exceeded global limit but < 120%; encourage retry with optional delay
 * - HARD: Client exceeded limit >= 120% or any client limit; immediate rejection
 */
public enum ThrottleType {
    NONE,
    SOFT,
    HARD
}
