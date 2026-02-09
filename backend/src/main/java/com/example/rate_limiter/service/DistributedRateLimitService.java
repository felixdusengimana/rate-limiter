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
     */
    public RateLimitResult tryConsume(UUID clientId) {
        Client client = clientRepository.findByIdWithSubscriptionPlan(clientId).orElse(null);
        if (client == null) {
            return RateLimitResult.builder().allowed(true).limit(0).current(0).remaining(Long.MAX_VALUE).retryAfterSeconds(0).exceededByType(null).globalUsageRatio(null).build();
        }

        List<EffectiveLimit> limits = effectiveLimitResolver.resolve(client);
        if (limits.isEmpty()) {
            return RateLimitResult.builder().allowed(true).limit(0).current(0).remaining(Long.MAX_VALUE).retryAfterSeconds(0).exceededByType(null).globalUsageRatio(null).build();
        }

        List<String> incrementedKeys = new java.util.ArrayList<>();
        long retryAfterSeconds = 0;
        Double globalUsageRatio = null;

        for (EffectiveLimit limit : limits) {
            String key = buildKey(limit, clientId);
            int ttlSeconds = getTtlSeconds(limit);
            long limitValue = limit.limitValue();

            RateLimitResult result = checkAndIncrement(key, limitValue, ttlSeconds);
            if (result.isAllowed()) {
                incrementedKeys.add(key);
                retryAfterSeconds = Math.max(retryAfterSeconds, result.getRetryAfterSeconds());
                if (limit.limitType() == RateLimitType.GLOBAL && result.getLimit() > 0) {
                    globalUsageRatio = (double) result.getCurrent() / result.getLimit();
                }
            } else {
                rollbackIncrements(incrementedKeys);
                log.debug("Rate limit exceeded: limitType={}, clientId={}, key={}", limit.limitType(), clientId, key);
                Double deniedRatio = limit.limitType() == RateLimitType.GLOBAL && result.getLimit() > 0
                        ? (double) result.getCurrent() / result.getLimit()
                        : null;
                return result.toBuilder()
                        .retryAfterSeconds(ttlSeconds)
                        .exceededByType(limit.limitType())
                        .globalUsageRatio(deniedRatio)
                        .build();
            }
        }

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

    private void rollbackIncrements(List<String> keys) {
        for (String key : keys) {
            redisTemplate.opsForValue().decrement(key);
        }
    }

    private String buildKey(EffectiveLimit limit, UUID clientId) {
        return switch (limit.limitType()) {
            case WINDOW -> KEY_PREFIX + "c:" + limit.clientId() + ":" + WINDOW_PREFIX + windowBucket(limit.windowSeconds());
            case MONTHLY -> KEY_PREFIX + "c:" + limit.clientId() + ":" + MONTHLY_PREFIX + monthBucket();
            case GLOBAL -> (limit.globalWindowSeconds() != null && limit.globalWindowSeconds() > 0)
                    ? KEY_PREFIX + "g:" + WINDOW_PREFIX + windowBucket(limit.globalWindowSeconds())
                    : KEY_PREFIX + "g:" + MONTHLY_PREFIX + monthBucket();
        };
    }

    private int getTtlSeconds(EffectiveLimit limit) {
        return switch (limit.limitType()) {
            case WINDOW -> limit.windowSeconds() != null ? limit.windowSeconds() : 60;
            case MONTHLY -> (int) ChronoUnit.SECONDS.between(Instant.now(), YearMonth.now().plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC));
            case GLOBAL -> limit.globalWindowSeconds() != null && limit.globalWindowSeconds() > 0
                    ? limit.globalWindowSeconds()
                    : (int) ChronoUnit.SECONDS.between(Instant.now(), YearMonth.now().plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC));
        };
    }

    private static long windowBucket(Integer windowSeconds) {
        int sec = (windowSeconds != null && windowSeconds > 0) ? windowSeconds : 60;
        return Instant.now().getEpochSecond() / sec * sec;
    }

    private static String monthBucket() {
        return YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private RateLimitResult checkAndIncrement(String key, long limit, int ttlSeconds) {
        // On deny we return (0, current, limit) so caller can compute globalUsageRatio = current/limit before we DECR
        String script = """
                local current = redis.call('INCR', KEYS[1])
                if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
                if current > tonumber(ARGV[2]) then
                  redis.call('DECR', KEYS[1])
                  return {0, current, tonumber(ARGV[2])}
                end
                return {1, current, tonumber(ARGV[2]) - current}
                """;
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>(script, List.class);
        @SuppressWarnings("unchecked")
        List<Long> result = (List<Long>) redisTemplate.execute(redisScript, List.of(key), String.valueOf(ttlSeconds), String.valueOf(limit));
        if (result == null || result.size() < 3) {
            return RateLimitResult.builder().allowed(true).limit(limit).current(0).remaining(limit).retryAfterSeconds(ttlSeconds).exceededByType(null).globalUsageRatio(null).build();
        }
        long allowed = result.get(0);
        long current = result.get(1);
        long third = result.get(2);
        long remaining = allowed == 1 ? Math.max(0, third) : 0; // on deny, Lua returns limit in third slot
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
}
