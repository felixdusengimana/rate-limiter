package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.RateLimitRule;
import com.example.rate_limiter.domain.RateLimitType;
import com.example.rate_limiter.repository.RateLimitRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributedRateLimitServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RateLimitRuleRepository ruleRepository;

    private DistributedRateLimitService rateLimitService;

    private UUID clientId;

    @BeforeEach
    void setUp() {
        rateLimitService = new DistributedRateLimitService(redisTemplate, ruleRepository);
        clientId = UUID.randomUUID();
    }

    @Test
    void tryConsume_allows_when_no_rules() {
        when(ruleRepository.findByActiveTrue()).thenReturn(List.of());
        RateLimitResult result = rateLimitService.tryConsume(clientId);
        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryConsume_allows_when_under_limit() {
        RateLimitRule rule = RateLimitRule.builder()
                .limitType(RateLimitType.WINDOW)
                .limitValue(10)
                .windowSeconds(60)
                .clientId(clientId)
                .active(true)
                .build();
        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(rule));
        when(redisTemplate.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(List.of(1L, 1L, 9L)); // allowed, current=1, remaining=9

        RateLimitResult result = rateLimitService.tryConsume(clientId);
        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryConsume_denies_when_over_limit() {
        RateLimitRule rule = RateLimitRule.builder()
                .limitType(RateLimitType.WINDOW)
                .limitValue(2)
                .windowSeconds(60)
                .clientId(clientId)
                .active(true)
                .build();
        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(rule));
        when(redisTemplate.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(List.of(0L, 3L, 2L)); // denied, current would be 3, limit 2

        RateLimitResult result = rateLimitService.tryConsume(clientId);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getLimit()).isEqualTo(2);
        assertThat(result.getRetryAfterSeconds()).isPositive();
        assertThat(result.getExceededByType()).isEqualTo(RateLimitType.WINDOW);
    }
}
