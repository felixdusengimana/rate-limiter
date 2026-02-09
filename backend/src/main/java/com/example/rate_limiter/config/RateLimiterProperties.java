package com.example.rate_limiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    /** hard = immediate 429; soft = 429 with Retry-After, optional delay */
    private String throttling = "hard";
    /** For soft throttling: optional delay in ms before returning 429 (0 = no delay) */
    private long softDelayMs = 0;
}
