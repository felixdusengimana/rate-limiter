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
 * Supports hard throttling (immediate 429) and soft (429 with Retry-After, optional delay).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

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
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "error", "Unauthorized",
                    "message", "Missing X-API-Key header"
            )));
            return;
        }

        Client client;
        try {
            client = clientService.getByApiKey(apiKey.trim());
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "error", "Unauthorized",
                    "message", "Invalid API key"
            )));
            return;
        }

        if (!client.isActive()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "error", "Forbidden",
                    "message", "Client is inactive"
            )));
            return;
        }

        RateLimitResult result = rateLimitService.tryConsume(client.getId());

        if (result.isAllowed()) {
            if (result.getLimit() > 0) {
                response.setHeader(RATE_LIMIT_LIMIT_HEADER, String.valueOf(result.getLimit()));
                response.setHeader(RATE_LIMIT_REMAINING_HEADER, String.valueOf(result.getRemaining()));
            }
            filterChain.doFilter(request, response);
            return;
        }

        // Rate limit exceeded: soft throttling only for GLOBAL limit; hard for WINDOW/MONTHLY (client-specific)
        boolean useSoftThrottling = result.getExceededByType() == RateLimitType.GLOBAL
                && "soft".equalsIgnoreCase(properties.getThrottling())
                && properties.getSoftDelayMs() > 0;
        if (useSoftThrottling) {
            try {
                Thread.sleep(properties.getSoftDelayMs());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        response.setStatus(429); // Too Many Requests
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
