package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.Client;
import com.example.rate_limiter.domain.RateLimitType;
import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributedRateLimitServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private EffectiveLimitResolver effectiveLimitResolver;

    private DistributedRateLimitService rateLimitService;

    private UUID clientId;
    private Client client;

    @BeforeEach
    void setUp() {
        rateLimitService = new DistributedRateLimitService(redisTemplate, clientRepository, effectiveLimitResolver);
        clientId = UUID.randomUUID();
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .name("Test")
                .monthlyLimit(1_000L)
                .active(true)
                .build();
        client = Client.builder()
                .id(clientId)
                .name("Test Client")
                .apiKey("rk_test")
                .subscriptionPlan(plan)
                .active(true)
                .build();
    }

    @Test
    void tryConsume_allows_when_client_not_found() {
        when(clientRepository.findByIdWithSubscriptionPlan(clientId)).thenReturn(Optional.empty());
        RateLimitResult result = rateLimitService.tryConsume(clientId);
        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    void tryConsume_allows_when_no_limits() {
        when(clientRepository.findByIdWithSubscriptionPlan(clientId)).thenReturn(Optional.of(client));
        when(effectiveLimitResolver.resolve(client)).thenReturn(List.of());
        RateLimitResult result = rateLimitService.tryConsume(clientId);
        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryConsume_allows_when_under_limit() {
        when(clientRepository.findByIdWithSubscriptionPlan(clientId)).thenReturn(Optional.of(client));
        when(effectiveLimitResolver.resolve(client)).thenReturn(
                List.of(EffectiveLimit.fromPlanMonthly(clientId, 10)));
        when(redisTemplate.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(List.of(1L, 1L, 9L)); // allowed, current=1, remaining=9

        RateLimitResult result = rateLimitService.tryConsume(clientId);
        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryConsume_denies_when_over_monthly_limit() {
        when(clientRepository.findByIdWithSubscriptionPlan(clientId)).thenReturn(Optional.of(client));
        when(effectiveLimitResolver.resolve(client)).thenReturn(
                List.of(EffectiveLimit.fromPlanMonthly(clientId, 2)));
        when(redisTemplate.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(List.of(0L, 3L, 2L)); // denied, current would be 3, limit 2

        RateLimitResult result = rateLimitService.tryConsume(clientId);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getLimit()).isEqualTo(2);
        assertThat(result.getRetryAfterSeconds()).isPositive();
        assertThat(result.getExceededByType()).isEqualTo(RateLimitType.MONTHLY);
    }
}
