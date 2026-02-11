package com.example.rate_limiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for rate limiting behavior.
 * Configurable from application.properties under the "rate-limiter" prefix.
 * 
 * Example:
 *   rate-limiter.throttling=soft
 *   rate-limiter.soft-delay-ms=500
 */
@Data
@Component
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    /**
     * Throttling strategy: "hard" (immediate rejection) or "soft" (rejection with optional delay).
     * Default: hard
     */
    private String throttling = "hard";

    /**
     * For soft throttling: optional delay in milliseconds before returning 429.
     * Applied only if throttling mode is "soft" and this value > 0.
     * Default: 100ms
     */
    private long softDelayMs = 100;

    /**
     * Global usage threshold for soft throttling (ratio).
     * Usage above this threshold (e.g., 0.80 for 80%) triggers soft throttling for global limits.
     * Default: 0.80
     */
    private double globalSoftThreshold = 0.80;

    /**
     * Global usage threshold for warning logs (ratio).
     * Usage above this threshold triggers warning logs.
     * Default: 0.80
     */
    private double globalWarnThreshold = 0.80;

    /**
     * Global usage threshold for full capacity logs (ratio).
     * Usage above this threshold (e.g., 1.00 for 100%) triggers full capacity warnings.
     * Default: 1.00
     */
    private double globalFullThreshold = 1.00;

    /**
     * Global usage threshold for hard throttling (ratio).
     * Usage above this threshold (e.g., 1.20 for 120%) triggers hard throttling for global limits.
     * Default: 1.20
     */
    private double globalHardThreshold = 1.20;
}
