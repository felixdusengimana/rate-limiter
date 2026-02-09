package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.Client;
import com.example.rate_limiter.domain.RateLimitType;
import com.example.rate_limiter.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Distributed rate limiting using Redis. Limits are derived from:
 * - Client's subscription plan (monthly + optional per-window)
 * - Global rules (from rate_limit_rules)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedRateLimitService {

    private static final String KEY_PREFIX = "rl:";
    private static final String WINDOW_PREFIX = "w:";
    private static final String MONTHLY_PREFIX = "m:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ClientRepository clientRepository;
    private final EffectiveLimitResolver effectiveLimitResolver;

    /**
     * Atomically check and consume one request for the given client. Applies limits from
     * the client's subscription plan (monthly, optional window) and global rules.
     * 
     * @return RateLimitResult with allowed flag, current usage, and retry metadata.
     */
    public RateLimitResult tryConsume(UUID clientId) {
        // Fetch client with subscription plan
        Client client = clientRepository.findByIdWithSubscriptionPlan(clientId).orElse(null);
        if (client == null) {
            return buildNoLimitResult();
        }

        // Resolve effective limits (merged from subscription plan + global rules)
        List<EffectiveLimit> limits = effectiveLimitResolver.resolve(client);
        if (limits.isEmpty()) {
            return buildNoLimitResult();
        }

        // Track incremented keys for rollback on failure
        List<String> incrementedKeys = new java.util.ArrayList<>();
        long retryAfterSeconds = 0;
        Double globalUsageRatio = null;

        // Check all limits atomically
        for (EffectiveLimit limit : limits) {
            String key = buildKey(limit, clientId);
            int ttlSeconds = getTtlSeconds(limit);
            long limitValue = limit.limitValue();

            RateLimitResult result = checkAndIncrement(key, limitValue, ttlSeconds);
            
            if (result.isAllowed()) {
                // Increment succeeded, track the key for potential rollback
                incrementedKeys.add(key);
                retryAfterSeconds = Math.max(retryAfterSeconds, result.getRetryAfterSeconds());
                
                // Update global usage ratio for monitoring
                if (limit.limitType() == RateLimitType.GLOBAL && result.getLimit() > 0) {
                    globalUsageRatio = (double) result.getCurrent() / result.getLimit();
                }
            } else {
                // Limit exceeded: rollback previous increments and return denial
                rollbackIncrements(incrementedKeys);
                log.debug("Rate limit exceeded: limitType={}, clientId={}, key={}", 
                         limit.limitType(), clientId, key);
                
                Double deniedRatio = calculateGlobalUsageRatio(limit, result);
                return result.toBuilder()
                        .retryAfterSeconds(ttlSeconds)
                        .exceededByType(limit.limitType())
                        .globalUsageRatio(deniedRatio)
                        .build();
            }
        }

        // All limits passed
        return RateLimitResult.builder()
                .allowed(true)
                .limit(0)
                .current(0)
                .remaining(Long.MAX_VALUE)
                .retryAfterSeconds(retryAfterSeconds)
                .exceededByType(null)
                .globalUsageRatio(globalUsageRatio)
                .build();
    }

    /**
     * Build a result for a client with no effective limits.
     */
    private RateLimitResult buildNoLimitResult() {
        return RateLimitResult.builder()
                .allowed(true)
                .limit(0)
                .current(0)
                .remaining(Long.MAX_VALUE)
                .retryAfterSeconds(0)
                .exceededByType(null)
                .globalUsageRatio(null)
                .build();
    }

    /**
     * Calculate global usage ratio (current / limit) when denied.
     */
    private Double calculateGlobalUsageRatio(EffectiveLimit limit, RateLimitResult result) {
        if (limit.limitType() == RateLimitType.GLOBAL && result.getLimit() > 0) {
            return (double) result.getCurrent() / result.getLimit();
        }
        return null;
    }

    private void rollbackIncrements(List<String> keys) {
        for (String key : keys) {
            redisTemplate.opsForValue().decrement(key);
        }
    }

    /**
     * Build Redis key based on the limit type and client ID.
     */
    private String buildKey(EffectiveLimit limit, UUID clientId) {
        return switch (limit.limitType()) {
            case WINDOW -> buildWindowKey(clientId, limit.windowSeconds());
            case MONTHLY -> buildMonthlyKey(clientId);
            case GLOBAL -> buildGlobalKey(limit.globalWindowSeconds());
        };
    }

    private String buildWindowKey(UUID clientId, Integer windowSeconds) {
        long bucket = windowBucket(windowSeconds);
        return KEY_PREFIX + "c:" + clientId + ":" + WINDOW_PREFIX + bucket;
    }

    private String buildMonthlyKey(UUID clientId) {
        String monthBucket = monthBucket();
        return KEY_PREFIX + "c:" + clientId + ":" + MONTHLY_PREFIX + monthBucket;
    }

    private String buildGlobalKey(Integer globalWindowSeconds) {
        if (globalWindowSeconds != null && globalWindowSeconds > 0) {
            long bucket = windowBucket(globalWindowSeconds);
            return KEY_PREFIX + "g:" + WINDOW_PREFIX + bucket;
        } else {
            String monthBucket = monthBucket();
            return KEY_PREFIX + "g:" + MONTHLY_PREFIX + monthBucket;
        }
    }

    /**
     * Calculate TTL (time-to-live) in seconds based on limit type.
     */
    private int getTtlSeconds(EffectiveLimit limit) {
        return switch (limit.limitType()) {
            case WINDOW -> getWindowTtl(limit.windowSeconds());
            case MONTHLY -> getMonthlyTtl();
            case GLOBAL -> getGlobalTtl(limit.globalWindowSeconds());
        };
    }

    private int getWindowTtl(Integer windowSeconds) {
        int sec = (windowSeconds != null && windowSeconds > 0) ? windowSeconds : 60;
        return sec;
    }

    private int getMonthlyTtl() {
        return (int) ChronoUnit.SECONDS.between(
                Instant.now(),
                YearMonth.now()
                        .plusMonths(1)
                        .atDay(1)
                        .atStartOfDay(ZoneOffset.UTC)
        );
    }

    private int getGlobalTtl(Integer globalWindowSeconds) {
        if (globalWindowSeconds != null && globalWindowSeconds > 0) {
            return globalWindowSeconds;
        }
        return getMonthlyTtl();
    }

    private static long windowBucket(Integer windowSeconds) {
        int sec = (windowSeconds != null && windowSeconds > 0) ? windowSeconds : 60;
        return Instant.now().getEpochSecond() / sec * sec;
    }

    private static String monthBucket() {
        return YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
    }

    /**
     * Atomic Redis operation: check limit and increment counter in one transaction.
     * Uses Lua script to avoid race conditions.
     * 
     * On deny: returns (0, current, limit) to compute globalUsageRatio before decrement.
     * On allow: returns (1, current, remaining).
     */
    private RateLimitResult checkAndIncrement(String key, long limit, int ttlSeconds) {
        String luaScript = buildLuaScript();
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>(luaScript, List.class);
        
        @SuppressWarnings("unchecked")
        List<Long> redisResult = (List<Long>) redisTemplate.execute(
                redisScript,
                List.of(key),
                String.valueOf(ttlSeconds),
                String.valueOf(limit)
        );

        return parseRedisResult(redisResult, limit, ttlSeconds);
    }

    /**
     * Lua script for atomic increment with limit check.
     */
    private String buildLuaScript() {
        return """
                local current = redis.call('INCR', KEYS[1])
                if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
                if current > tonumber(ARGV[2]) then
                  redis.call('DECR', KEYS[1])
                  return {0, current, tonumber(ARGV[2])}
                end
                return {1, current, tonumber(ARGV[2]) - current}
                """;
    }

    /**
     * Parse Redis Lua script result into RateLimitResult.
     */
    private RateLimitResult parseRedisResult(List<Long> result, long limit, int ttlSeconds) {
        // Handle null or incomplete result
        if (result == null || result.size() < 3) {
            return buildDefaultAllowedResult(limit, ttlSeconds);
        }

        long allowed = result.get(0);
        long current = result.get(1);
        long third = result.get(2);
        
        // On allow: third = remaining; on deny: third = limit
        long remaining = (allowed == 1) ? Math.max(0, third) : 0;

        return RateLimitResult.builder()
                .allowed(allowed == 1)
                .limit(limit)
                .current(current)
                .remaining(remaining)
                .retryAfterSeconds(ttlSeconds)
                .exceededByType(null)
                .globalUsageRatio(null)
                .build();
    }

    /**
     * Default result when Redis returns null or invalid data.
     */
    private RateLimitResult buildDefaultAllowedResult(long limit, int ttlSeconds) {
        return RateLimitResult.builder()
                .allowed(true)
                .limit(limit)
                .current(0)
                .remaining(limit)
                .retryAfterSeconds(ttlSeconds)
                .exceededByType(null)
                .globalUsageRatio(null)
                .build();
    }
}
