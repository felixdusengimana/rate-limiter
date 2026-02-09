package com.example.rate_limiter.filter;

import com.example.rate_limiter.config.RateLimiterProperties;
import com.example.rate_limiter.domain.Client;
import com.example.rate_limiter.domain.RateLimitType;
import com.example.rate_limiter.service.ClientService;
import com.example.rate_limiter.service.DistributedRateLimitService;
import com.example.rate_limiter.service.RateLimitResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Applies rate limiting to /api/notify/** using X-API-Key to identify the client.
 * - Client limits (subscription): hard throttling (immediate 429).
 * - Global (system) limit: soft until 120% usage, then hard; logs at 80%/100% with TODO to notify admin.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final double GLOBAL_WARN_THRESHOLD = 0.80;
    private static final double GLOBAL_FULL_THRESHOLD = 1.00;
    private static final double GLOBAL_HARD_THRESHOLD = 1.20;

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private final ClientService clientService;
    private final DistributedRateLimitService rateLimitService;
    private final RateLimiterProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/notify/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Extract and validate API key
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            sendUnauthorizedResponse(response, "Missing X-API-Key header");
            return;
        }

        // Resolve client from API key
        Client client;
        try {
            client = clientService.getByApiKey(apiKey.trim());
        } catch (IllegalArgumentException e) {
            sendUnauthorizedResponse(response, "Invalid API key");
            return;
        }

        // Check if client is active
        if (!client.isActive()) {
            sendForbiddenResponse(response, "Client is inactive");
            return;
        }

        // Check rate limits
        RateLimitResult result = rateLimitService.tryConsume(client.getId());

        // Request allowed
        if (result.isAllowed()) {
            setRateLimitHeaders(response, result);
            logGlobalWarningsIfNeeded(result);
            filterChain.doFilter(request, response);
            return;
        }

        // Request denied: handle throttling and response
        handleRateLimitDenied(response, result);
    }

    /**
     * Send 401 Unauthorized response with error message.
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "error", "Unauthorized",
                "message", message
        )));
    }

    /**
     * Send 403 Forbidden response with error message.
     */
    private void sendForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "error", "Forbidden",
                "message", message
        )));
    }

    /**
     * Set rate limit headers (X-RateLimit-Limit, X-RateLimit-Remaining) when request is allowed.
     */
    private void setRateLimitHeaders(HttpServletResponse response, RateLimitResult result) {
        if (result.getLimit() > 0) {
            response.setHeader(RATE_LIMIT_LIMIT_HEADER, String.valueOf(result.getLimit()));
            response.setHeader(RATE_LIMIT_REMAINING_HEADER, String.valueOf(result.getRemaining()));
        }
    }

    /**
     * Log warnings when global usage reaches thresholds (80%, 100%+).
     */
    private void logGlobalWarningsIfNeeded(RateLimitResult result) {
        if (result.getGlobalUsageRatio() == null) {
            return;
        }

        if (result.getGlobalUsageRatio() >= GLOBAL_WARN_THRESHOLD) {
            log.warn("Global rate limit usage at {}% - consider notifying administrator. " +
                    "TODO: notify system administrator (e.g. send email)",
                    String.format("%.0f", result.getGlobalUsageRatio() * 100));
        }
    }

    /**
     * Handle rate limit denial: apply soft throttling if configured, then send 429 response.
     */
    private void handleRateLimitDenied(HttpServletResponse response, RateLimitResult result) 
            throws IOException {
        
        // Determine throttling approach
        boolean isGlobalExceeded = result.getExceededByType() == RateLimitType.GLOBAL;
        Double ratio = result.getGlobalUsageRatio();
        boolean useHardForGlobal = isGlobalExceeded && ratio != null && ratio >= GLOBAL_HARD_THRESHOLD;
        boolean useSoftThrottling = isGlobalExceeded && !useHardForGlobal
                && "soft".equalsIgnoreCase(properties.getThrottling())
                && properties.getSoftDelayMs() > 0;

        // Log if global limit at or over 100%
        if (isGlobalExceeded && ratio != null && ratio >= GLOBAL_FULL_THRESHOLD) {
            log.warn("Global rate limit at or over 100% - rejecting request. " +
                    "TODO: notify system administrator");
        }

        // Apply soft throttling delay if configured
        if (useSoftThrottling) {
            applySoftThrottlingDelay();
        }

        // Send 429 Too Many Requests
        send429Response(response, result);
    }

    /**
     * Apply soft throttling delay (configurable sleep before rejection).
     */
    private void applySoftThrottlingDelay() {
        try {
            Thread.sleep(properties.getSoftDelayMs());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Send 429 Too Many Requests response with rate limit metadata.
     */
    private void send429Response(HttpServletResponse response, RateLimitResult result) throws IOException {
        response.setStatus(429);
        response.setHeader(RETRY_AFTER_HEADER, String.valueOf(result.getRetryAfterSeconds()));
        
        if (result.getLimit() > 0) {
            response.setHeader(RATE_LIMIT_LIMIT_HEADER, String.valueOf(result.getLimit()));
            response.setHeader(RATE_LIMIT_REMAINING_HEADER, "0");
        }
        
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "error", "Too Many Requests",
                "message", "Rate limit exceeded. Retry after " + result.getRetryAfterSeconds() + " seconds.",
                "retryAfterSeconds", result.getRetryAfterSeconds()
        )));
    }
}
