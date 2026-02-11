package com.example.rate_limiter.filter;

import com.example.rate_limiter.config.RateLimiterProperties;
import com.example.rate_limiter.domain.Client;
import com.example.rate_limiter.domain.RateLimitType;
import com.example.rate_limiter.domain.ThrottleType;
import com.example.rate_limiter.service.ClientService;
import com.example.rate_limiter.service.DistributedRateLimitService;
import com.example.rate_limiter.service.RateLimitResult;
import com.example.rate_limiter.util.TimeFormatUtil;
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
import java.util.ArrayList;
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

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String RETRY_AFTER_HEADER = "Retry-After";
    private static final String THROTTLE_TYPE_HEADER = "X-Throttle-Type";
    private static final String THROTTLE_DELAY_HEADER = "X-Suggested-Delay-Ms";

    private final ClientService clientService;
    private final DistributedRateLimitService rateLimitService;
    private final RateLimiterProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/notify/");
    }

    /**
     * Add CORS headers to allow all origins to read the response.
     */
    private void addCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin != null) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Expose-Headers", 
                    "Content-Type, X-RateLimit-Limit, X-RateLimit-Remaining, Retry-After, X-Throttle-Type, X-Suggested-Delay-Ms");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Add CORS headers for responses from this filter
        addCorsHeaders(request, response);
        
        // Skip rate limiting for CORS preflight (OPTIONS) requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

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
        try {
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
        } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
            log.error("Redis connection failed while checking rate limits", e);
            sendServiceUnavailableResponse(response, "Rate limiting service temporarily unavailable");
        } catch (Exception e) {
            log.error("Unexpected error in rate limit filter", e);
            sendServiceUnavailableResponse(response, "An error occurred while processing your request");
        }
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
     * Send 503 Service Unavailable response with error message.
     */
    private void sendServiceUnavailableResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "error", "Service Unavailable",
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

        if (result.getGlobalUsageRatio() >= properties.getGlobalWarnThreshold() && result.getGlobalUsageRatio() < properties.getGlobalFullThreshold()) {
            log.warn("Global rate limit usage at {}% notifying administrator. " +
                    "TODO: notify system administrator (e.g. send email)",
                    String.format("%.0f", result.getGlobalUsageRatio() * 100));
        }
    }

    /**
     * Handle rate limit denial: apply soft throttling if configured, then send 429 response.
     */
    private void handleRateLimitDenied(HttpServletResponse response, RateLimitResult result) 
            throws IOException {
        
        // Log if global limit at or over 100%
        if (result.getExceededByType() == RateLimitType.GLOBAL && result.getGlobalUsageRatio() != null) {
            if (result.getGlobalUsageRatio() >= properties.getGlobalFullThreshold() && result.getGlobalUsageRatio() < properties.getGlobalHardThreshold()) {
                log.warn("Global rate limit at or over 100% - rejecting request. " +
                        "TODO: notify system administrator");
            }
        }

        // Apply soft throttling delay if configured
        if (result.getThrottleType() == ThrottleType.SOFT && result.getSoftDelayMs() > 0) {
            try {
                Thread.sleep(result.getSoftDelayMs());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        // Send 429 Too Many Requests
        send429Response(response, result);
    }

    /**
     * Send 429 Too Many Requests response with rate limit metadata.
     */
    private void send429Response(HttpServletResponse response, RateLimitResult result) throws IOException {
        response.setStatus(429);
        response.setHeader(RETRY_AFTER_HEADER, String.valueOf(result.getRetryAfterSeconds()));
        response.setHeader(THROTTLE_TYPE_HEADER, result.getThrottleType().toString());
        
        if (result.getSoftDelayMs() > 0) {
            response.setHeader(THROTTLE_DELAY_HEADER, String.valueOf(result.getSoftDelayMs()));
        }
        
        if (result.getLimit() > 0) {
            response.setHeader(RATE_LIMIT_LIMIT_HEADER, String.valueOf(result.getLimit()));
            response.setHeader(RATE_LIMIT_REMAINING_HEADER, "0");
        }
        
        // Build detailed error message based on which limit was exceeded
        String limitTypeDesc = result.getExceededByType() == RateLimitType.GLOBAL 
            ? "Global system limit"
            : "Your subscription plan limit";
        
        String formattedDuration = TimeFormatUtil.formatDuration(result.getRetryAfterSeconds());
        String detailMessage = String.format(
            "%s exhausted. Limit: %d requests. Retry after %s.",
            limitTypeDesc,
            result.getLimit(),
            formattedDuration
        );
        
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "error", "Too Many Requests",
                "message", detailMessage,
                "limitType", result.getExceededByType().toString(),
                "throttleType", result.getThrottleType().toString(),
                "limit", result.getLimit(),
                "current", result.getCurrent(),
                "retryAfterSeconds", result.getRetryAfterSeconds(),
                "retryAfterFormatted", formattedDuration,
                "suggestedDelayMs", result.getSoftDelayMs()
        )));
    }
}
