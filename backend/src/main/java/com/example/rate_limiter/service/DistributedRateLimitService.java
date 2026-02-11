package com.example.rate_limiter.service;

import com.example.rate_limiter.config.RateLimiterProperties;
import com.example.rate_limiter.domain.Client;
import com.example.rate_limiter.domain.RateLimitType;
import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.domain.ThrottleType;
import com.example.rate_limiter.repository.ClientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private static final String SUBSCRIPTION_CACHE_PREFIX = "sub:cache:";  // ✅ Cache subscription info
    private static final long DEFAULT_SUB_CACHE_TTL_SECONDS = 3600;  // 1 hour default
    

    private final RedisTemplate<String, String> redisTemplate;
    private final ClientRepository clientRepository;
    private final EffectiveLimitResolver effectiveLimitResolver;
    private final ObjectMapper objectMapper;
    private final RateLimiterProperties rateLimiterProperties;

    /**
     * Atomically check and consume one request for the given client. Applies limits from
     * the client's subscription plan (monthly, optional window) and global rules.
     * ALL clients MUST have an active subscription to consume APIs.
     * 
     * Uses cache-aside pattern: Check Redis cache first, only fetch from DB on cache miss.
     * Subscription info cached with TTL based on expiry date to prevent DB thrashing.
     * 
     * @return RateLimitResult with allowed flag, current usage, and retry metadata.
     */
    public RateLimitResult tryConsume(UUID clientId) {
        // ✅ CACHE-ASIDE: Try Redis first before hitting DB
        SubscriptionPlan plan = getOrFetchSubscriptionPlan(clientId);
        if (plan == null) {
            return buildSubscriptionRequiredResult("Client not found or no subscription");
        }

        // Check if subscription is expired
        if (!plan.isEffectivelyActive()) {
            cacheExpiredSubscription(clientId);  // ✅ Cache the "expired" state briefly
            return buildSubscriptionRequiredResult("Subscription plan is expired or inactive");
        }

        // ✅ Cache the valid subscription for future requests
        cacheSubscriptionPlan(clientId, plan);

        // Resolve effective limits (merged from subscription plan + global rules)
        Client cachedClient = rebuildClientFromPlan(clientId, plan);
        List<EffectiveLimit> limits = effectiveLimitResolver.resolve(cachedClient);
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
                ThrottleType throttleType = determineThrottleType(limit, result, deniedRatio);
                long softDelay = (throttleType == ThrottleType.SOFT) ? rateLimiterProperties.getSoftDelayMs() : 0;
                
                return result.toBuilder()
                        .retryAfterSeconds(ttlSeconds)
                        .exceededByType(limit.limitType())
                        .globalUsageRatio(deniedRatio)
                        .throttleType(throttleType)
                        .softDelayMs(softDelay)
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
                .throttleType(ThrottleType.NONE)
                .softDelayMs(0)
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
                .throttleType(ThrottleType.NONE)
                .softDelayMs(0)
                .build();
    }

    /**
     * Build a denial result when subscription requirement is not met.
     */
    private RateLimitResult buildSubscriptionRequiredResult(String reason) {
        log.warn("Subscription requirement not met: {}", reason);
        return RateLimitResult.builder()
                .allowed(false)
                .limit(0)
                .current(0)
                .remaining(0)
                .retryAfterSeconds(0)
                .exceededByType(null)
                .globalUsageRatio(null)
                .throttleType(ThrottleType.HARD)
                .softDelayMs(0)
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
                .throttleType(ThrottleType.NONE)
                .softDelayMs(0)
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
                .throttleType(ThrottleType.NONE)
                .softDelayMs(0)
                .build();
    }

    /**
     * Determine throttle strategy based on limit type and usage ratio.
     * - HARD: Client subscription limits (WINDOW/MONTHLY) or global >= 120%
     * - SOFT: Global limit 80-120% usage
     * - NONE: Request allowed
     */
    private ThrottleType determineThrottleType(EffectiveLimit limit, RateLimitResult result, Double globalUsageRatio) {
        // Client subscription limits (WINDOW/MONTHLY) always get hard throttle
        if (limit.limitType() == RateLimitType.WINDOW || limit.limitType() == RateLimitType.MONTHLY) {
            return ThrottleType.HARD;
        }
        
        // Global limits use ratio-based throttling
        if (limit.limitType() == RateLimitType.GLOBAL && globalUsageRatio != null) {
            if (globalUsageRatio >= rateLimiterProperties.getGlobalHardThreshold()) {
                return ThrottleType.HARD;           // >= 120%: immediate rejection
            } else if (globalUsageRatio >= rateLimiterProperties.getGlobalSoftThreshold()) {
                return ThrottleType.SOFT;           // 80-120%: request client retry with delay
            }
        }
        
        return ThrottleType.HARD;  // Default to hard for safety
    }

    /**
     * CACHE-ASIDE: Fetch subscription plan from Redis cache, or fall back to DB.
     * Only calls database on cache miss.
     */
    private SubscriptionPlan getOrFetchSubscriptionPlan(UUID clientId) {
        String cacheKey = SUBSCRIPTION_CACHE_PREFIX + clientId;
        
        try {
            // ✅ Try Redis cache first
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Subscription cache HIT for client: {}", clientId);
                // Check if it's a "null marker" (expired or not found)
                if ("EXPIRED".equals(cached)) {
                    return null;
                }
                return objectMapper.readValue(cached, SubscriptionPlan.class);
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize subscription cache for {}: {}", clientId, e.getMessage());
        }

        // ✅ Cache MISS: Fetch from database
        log.debug("Subscription cache MISS for client: {}, fetching from DB", clientId);
        Optional<Client> clientOptional = clientRepository.findByIdWithSubscriptionPlan(clientId);
        
        if (clientOptional.isEmpty() || clientOptional.get().getSubscriptionPlan() == null) {
            // Cache the "not found" state briefly (5 min) to prevent repeated DB hits
            redisTemplate.opsForValue().set(cacheKey, "EXPIRED", 300, TimeUnit.SECONDS);
            return null;
        }

        return clientOptional.get().getSubscriptionPlan();
    }

    /**
     * Cache subscription plan in Redis with smart TTL:
     * - If subscription has expiry date: TTL = time until expiry
     * - Otherwise: TTL = 1 hour (default)
     * 
     * This prevents DB thrashing while ensuring expired subscriptions are caught quickly.
     */
    private void cacheSubscriptionPlan(UUID clientId, SubscriptionPlan plan) {
        String cacheKey = SUBSCRIPTION_CACHE_PREFIX + clientId;
        
        try {
            String serialized = objectMapper.writeValueAsString(plan);
            long ttlSeconds = calculateSubscriptionCacheTTL(plan);
            redisTemplate.opsForValue().set(cacheKey, serialized, ttlSeconds, TimeUnit.SECONDS);
            log.debug("Cached subscription for client: {} with TTL: {} seconds", clientId, ttlSeconds);
        } catch (Exception e) {
            log.warn("Failed to cache subscription for {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * Mark subscription as inactive in cache (either expired or manually disabled).
     * Cache for 5 minutes to prevent immediate re-checks from DB.
     * Prevents DB queries if client re-requests soon after admin disables subscription.
     */
    private void cacheExpiredSubscription(UUID clientId) {
        String cacheKey = SUBSCRIPTION_CACHE_PREFIX + clientId;
        redisTemplate.opsForValue().set(cacheKey, "EXPIRED", 300, TimeUnit.SECONDS);  // 5 min
    }

    /**
     * Calculate appropriate TTL for subscription cache.
     * If subscription expires in 1 hour: cache for 30 min (refresh buffer)
     * If subscription expires in 24 hours: cache for 6 hours
     * If no expiry: cache for default 1 hour
     */
    private long calculateSubscriptionCacheTTL(SubscriptionPlan plan) {
        if (plan.getExpiresAt() == null) {
            return DEFAULT_SUB_CACHE_TTL_SECONDS;  // 1 hour
        }

        long secondsUntilExpiry = ChronoUnit.SECONDS.between(Instant.now(), plan.getExpiresAt());
        if (secondsUntilExpiry <= 0) {
            return 60;  // Already expired, cache for just 1 minute
        }

        // Cache for 50% of remaining time (with minimum 1 min, max 1 hour buffer)
        long halfLife = secondsUntilExpiry / 2;
        return Math.min(Math.max(halfLife, 60), DEFAULT_SUB_CACHE_TTL_SECONDS);
    }

    /**
     * Rebuild a minimal Client object from cached SubscriptionPlan for the resolver.
     * Avoids need to keep full Client object in cache.
     */
    private Client rebuildClientFromPlan(UUID clientId, SubscriptionPlan plan) {
        return Client.builder()
                .id(clientId)
                .name("cached")  // Minimal data
                .subscriptionPlan(plan)
                .build();
    }
}
