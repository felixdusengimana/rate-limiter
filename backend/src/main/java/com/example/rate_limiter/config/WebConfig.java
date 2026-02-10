package com.example.rate_limiter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration for CORS and HTTP settings.
 * Allows the Angular frontend (http://localhost:4200) to make API requests to the backend.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Configure CORS (Cross-Origin Resource Sharing) to allow the frontend to communicate with the backend.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(
                        "Content-Type",
                        "X-RateLimit-Limit",
                        "X-RateLimit-Remaining",
                        "Retry-After"
                )
                .allowCredentials(false);
    }
}
