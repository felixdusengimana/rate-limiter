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
     * Optimized: All limits checked with single Lua script execution (no loop, single Redis RT).
     * 
     * Check order: Global → Monthly → Window (priority-based)
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

        return checkAndIncrementBatch(limits, clientId);
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
     * ✅ DEPRECATED: No longer needed with batch processing.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @SuppressWarnings("unused")
    private Double calculateGlobalUsageRatio(EffectiveLimit limit, RateLimitResult result) {
        if (limit.limitType() == RateLimitType.GLOBAL && result.getLimit() > 0) {
            return (double) result.getCurrent() / result.getLimit();
        }
        return null;
    }

    /**
     * ✅ DEPRECATED: No longer needed with batch Lua script.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @SuppressWarnings("unused")
    private void rollbackIncrements(List<String> keys) {
        for (String key : keys) {
            redisTemplate.opsForValue().decrement(key);
        }
    }

    /**
     * Check and increment ALL limits atomically in one Lua script execution.
     * Eliminates loop and reduces Redis round-trips from 3 (or N) to exactly 1.
     * 
     * Limits checked in priority order: GLOBAL → MONTHLY → WINDOW
     * 
     * Lua script returns:
     * - [0, index, current, limit] if limit at 'index' exceeded (early return, no increments)
     * - [1, globalUsageRatio * 1000, retryAfterSeconds] if all limits pass (all incremented atomically)
     * 
     * @param limits list of EffectiveLimit to check
     * @param clientId client UUID for key building
     * @return RateLimitResult based on batch check result
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private RateLimitResult checkAndIncrementBatch(List<EffectiveLimit> limits, UUID clientId) {
        // Sort limits by priority: GLOBAL → MONTHLY → WINDOW
        List<EffectiveLimit> sortedLimits = limits.stream()
                .sorted((a, b) -> {
                    int priorityA = getLimitPriority(a.limitType());
                    int priorityB = getLimitPriority(b.limitType());
                    return Integer.compare(priorityA, priorityB);
                })
                .toList();

        // Build keys, limits, and TTLs arrays for Lua script
        List<String> keys = new java.util.ArrayList<>();
        List<String> limitValues = new java.util.ArrayList<>();
        List<String> ttlValues = new java.util.ArrayList<>();
        List<RateLimitType> limitTypes = new java.util.ArrayList<>();
        List<Integer> retryAfters = new java.util.ArrayList<>();

        for (EffectiveLimit limit : sortedLimits) {
            String key = buildKey(limit, clientId);
            int ttl = getTtlSeconds(limit);
            
            keys.add(key);
            limitValues.add(String.valueOf(limit.limitValue()));
            ttlValues.add(String.valueOf(ttl));
            limitTypes.add(limit.limitType());
            retryAfters.add(ttl);
        }

        // Execute single Lua script for all limits
        String luaScript = buildBatchLuaScript(sortedLimits.size());
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>(luaScript, List.class);
        
        List<String> args = new java.util.ArrayList<>();
        args.addAll(ttlValues);      // ARGV[1..n]: TTLs
        args.addAll(limitValues);    // ARGV[n+1..2n]: Limit values
        
        List<Long> redisResult = (List<Long>) redisTemplate.execute(
                redisScript,
                keys,
                (Object[]) args.toArray()
        );

        return parseBatchResult(redisResult, sortedLimits, retryAfters, clientId);
    }

    /**
     * Get limit priority: lower number = higher priority (checked first)
     */
    private int getLimitPriority(RateLimitType type) {
        return switch (type) {
            case GLOBAL -> 1;    // Check first
            case MONTHLY -> 2;   // Check second
            case WINDOW -> 3;    // Check third
        };
    }

    /**
     * Single comprehensive Lua script checking all limits atomically.
     * No loops in Java, all logic in Redis to avoid N round-trips.
     * 
     * Checks each limit in order, returns early if any fails.
     * On success: increments all counters atomically.
     * 
     * Args:
     * - ARGV[1..N]: TTL seconds for each limit
     * - ARGV[N+1..2N]: Limit values for each limit
     * 
     * @param limitCount number of limits to check
     * @return Lua script returning [status, data...]
     */
    @SuppressWarnings("StringConcatenationInLoop")
    private String buildBatchLuaScript(int limitCount) {
        StringBuilder script = new StringBuilder();
        script.append("local status = 1\n");
        script.append("local failed_idx = 0\n");
        script.append("local failed_current = 0\n");
        script.append("local failed_limit = 0\n");
        script.append("local global_ratio = 0\n");
        script.append("local max_retry = 0\n");
        script.append("\n");
        
        // Phase 1: Check all limits without incrementing
        for (int i = 0; i < limitCount; i++) {
            int argIdxLimit = limitCount + i + 1;  // ARGV[N+1..2N] = Limits
            int keyIdx = i + 1;  // KEYS[1..N]
            
            script.append("if status == 1 then\n");
            script.append("  local current").append(i).append(" = tonumber(redis.call('GET', KEYS[").append(keyIdx).append("]) or 0)\n");
            script.append("  local limit").append(i).append(" = tonumber(ARGV[").append(argIdxLimit).append("])\n");
            script.append("  if current").append(i).append(" >= limit").append(i).append(" then\n");
            script.append("    status = 0\n");
            script.append("    failed_idx = ").append(i).append("\n");
            script.append("    failed_current = current").append(i).append("\n");
            script.append("    failed_limit = limit").append(i).append("\n");
            script.append("  end\n");
            script.append("end\n");
        }
        
        script.append("\n");
        
        // Phase 2: If all limits pass, increment all atomically
        script.append("if status == 1 then\n");
        
        for (int i = 0; i < limitCount; i++) {
            int argIdxTtl = i + 1;  // ARGV[1..N] = TTLs
            int keyIdx = i + 1;  // KEYS[1..N]
            
            script.append("  local current").append(i).append(" = redis.call('INCR', KEYS[").append(keyIdx).append("])\n");
            script.append("  if current").append(i).append(" == 1 then\n");
            script.append("    redis.call('EXPIRE', KEYS[").append(keyIdx).append("], ARGV[").append(argIdxTtl).append("])\n");
            script.append("  end\n");
        }
        
        script.append("  max_retry = 0\n");
        
        for (int i = 0; i < limitCount; i++) {
            int argIdxTtl = i + 1;
            script.append("  if tonumber(ARGV[").append(argIdxTtl).append("]) > max_retry then\n");
            script.append("    max_retry = tonumber(ARGV[").append(argIdxTtl).append("])\n");
            script.append("  end\n");
        }
        
        script.append("  global_ratio = 0\n"); // Calculate global ratio if needed in Java
        script.append("  return {1, max_retry}\n");
        script.append("else\n");
        script.append("  return {0, failed_idx, failed_current, failed_limit}\n");
        script.append("end\n");
        
        return script.toString();
    }

    /**
     * Parse batch result from optimized Lua script.
     * 
     * Result format:
     * - [1, maxRetrySeconds]: All limits passed, all incremented atomically
     * - [0, failedIdx, currentValue, limitValue]: Limit at failedIdx exceeded
     */
    private RateLimitResult parseBatchResult(List<Long> result, List<EffectiveLimit> sortedLimits, 
                                             List<Integer> retryAfters, UUID clientId) {
        if (result == null || result.isEmpty()) {
            return buildNoLimitResult();
        }

        long status = result.get(0);

        if (status == 1) {
            // All limits passed
            long maxRetry = result.size() > 1 ? result.get(1) : 0;
            return RateLimitResult.builder()
                    .allowed(true)
                    .limit(0)
                    .current(0)
                    .remaining(Long.MAX_VALUE)
                    .retryAfterSeconds(maxRetry)
                    .exceededByType(null)
                    .globalUsageRatio(null)
                    .throttleType(ThrottleType.NONE)
                    .softDelayMs(0)
                    .build();
        } else {
            // A limit was exceeded
            int failedIdx = result.size() > 1 ? Math.toIntExact(result.get(1)) : 0;
            long failedCurrent = result.size() > 2 ? result.get(2) : 0;
            long failedLimit = result.size() > 3 ? result.get(3) : 0;

            if (failedIdx < sortedLimits.size()) {
                EffectiveLimit failedLimit_ = sortedLimits.get(failedIdx);
                int ttlSeconds = retryAfters.get(failedIdx);
                
                log.debug("Rate limit exceeded: limitType={}, clientId={}, current={}, limit={}", 
                         failedLimit_.limitType(), clientId, failedCurrent, failedLimit);

                Double globalUsageRatio = null;
                if (failedLimit_.limitType() == RateLimitType.GLOBAL && failedLimit > 0) {
                    globalUsageRatio = (double) failedCurrent / failedLimit;
                }

                ThrottleType throttleType = determineThrottleType(failedLimit_, 
                        RateLimitResult.builder().limit(failedLimit).current(failedCurrent).build(), 
                        globalUsageRatio);
                long softDelay = (throttleType == ThrottleType.SOFT) ? rateLimiterProperties.getSoftDelayMs() : 0;

                return RateLimitResult.builder()
                        .allowed(false)
                        .limit(failedLimit)
                        .current(failedCurrent)
                        .remaining(0)
                        .retryAfterSeconds(ttlSeconds)
                        .exceededByType(failedLimit_.limitType())
                        .globalUsageRatio(globalUsageRatio)
                        .throttleType(throttleType)
                        .softDelayMs(softDelay)
                        .build();
            }
        }

        return buildNoLimitResult();
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
     * ✅ DEPRECATED: Use checkAndIncrementBatch instead for better performance (single RT).
     * Kept for reference/backwards compatibility.
     * 
     * On deny: returns (0, current, limit) to compute globalUsageRatio before decrement.
     * On allow: returns (1, current, remaining).
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @SuppressWarnings({"unchecked", "rawtypes", "unused"})
    private RateLimitResult checkAndIncrement(String key, long limit, int ttlSeconds) {
        String luaScript = buildLuaScript();
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>(luaScript, List.class);
        
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
     * ✅ DEPRECATED: Use buildBatchLuaScript instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
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
     * ✅ DEPRECATED: Use parseBatchResult instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
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


    /**
     * Clear ALL Redis data for a specific client: subscription cache and all rate limit counters.
     * Called when a client's plan is updated to reset their rate limit usage.
     * 
     * Clears:
     * - Subscription plan cache (sub:cache:{clientId})
     * - Window limit counters (rl:c:{clientId}:w:*)
     * - Monthly limit counters (rl:c:{clientId}:m:*)
     * 
     * @param clientId the client UUID
     */
    public void clearAllClientRedisData(UUID clientId) {
        try {
            // Clear subscription cache
            String subscriptionCacheKey = SUBSCRIPTION_CACHE_PREFIX + clientId;
            redisTemplate.delete(subscriptionCacheKey);
            
            // Clear all rate limit keys for this client using pattern scan
            String clientKeyPattern = KEY_PREFIX + "c:" + clientId + ":*";
            redisTemplate.keys(clientKeyPattern).forEach(key -> {
                try {
                    redisTemplate.delete(key);
                    log.debug("Deleted Redis key: {}", key);
                } catch (Exception e) {
                    log.warn("Failed to delete Redis key {}: {}", key, e.getMessage());
                }
            });
            
            log.info("✅ Cleared all Redis data for client: {}", clientId);
        } catch (Exception e) {
            log.warn("Failed to clear Redis data for client {}: {}", clientId, e.getMessage());
        }
    }
}
