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
     * Default: 0 (no delay)
     */
    private long softDelayMs = 0;
}
