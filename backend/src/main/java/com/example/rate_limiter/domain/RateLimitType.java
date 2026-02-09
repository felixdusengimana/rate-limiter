package com.example.rate_limiter.domain;

/**
 * Type of rate limit: per-client time window, per-client monthly, or global.
 */
public enum RateLimitType {
    /** Requests per time window (e.g. per minute) for a specific client */
    WINDOW,
    /** Requests per calendar month for a specific client */
    MONTHLY,
    /** Requests across the entire system (window or monthly) */
    GLOBAL
}
