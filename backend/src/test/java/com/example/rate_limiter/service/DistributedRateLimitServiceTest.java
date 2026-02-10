package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.Client;
import com.example.rate_limiter.domain.RateLimitType;
import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.repository.ClientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
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
    private ValueOperations<String, String> redisOps;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private EffectiveLimitResolver effectiveLimitResolver;

    private ObjectMapper objectMapper;

    private DistributedRateLimitService rateLimitService;

    private UUID clientId;
    private Client client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();  // ✅ Real ObjectMapper for serialization
        rateLimitService = new DistributedRateLimitService(redisTemplate, clientRepository, effectiveLimitResolver, objectMapper);
        clientId = UUID.randomUUID();
        
        // ✅ Mock Redis cache operations (cache miss by default)
        when(redisTemplate.opsForValue()).thenReturn(redisOps);
        when(redisOps.get(any())).thenReturn(null);  // Cache MISS by default
        
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .name("Test")
                .monthlyLimit(1_000L)
                .build();
        client = Client.builder()
                .id(clientId)
                .name("Test Client")
                .apiKey("rk_test")
                .subscriptionPlan(plan)
                .build();
    }

    @Test
    void tryConsume_denies_when_client_not_found() {
        when(clientRepository.findByIdWithSubscriptionPlan(clientId)).thenReturn(Optional.empty());
        RateLimitResult result = rateLimitService.tryConsume(clientId);
        assertThat(result.isAllowed()).isFalse();  // ✅ DENY if client not found
    }

    @Test
    void tryConsume_denies_when_no_subscription_plan() {
        Client clientNoSub = Client.builder()
                .id(clientId)
                .name("No Subscription Client")
                .apiKey("rk_nosub")
                .subscriptionPlan(null)  // ❌ No subscription
                .build();
        when(clientRepository.findByIdWithSubscriptionPlan(clientId)).thenReturn(Optional.of(clientNoSub));
        RateLimitResult result = rateLimitService.tryConsume(clientId);
        assertThat(result.isAllowed()).isFalse();  // ✅ DENY if no subscription
    }

    @Test
    void tryConsume_denies_when_subscription_expired() {
        SubscriptionPlan expiredPlan = SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .name("Expired Plan")
                .monthlyLimit(1_000L)
                .expiresAt(Instant.now().minusSeconds(3600))  // ❌ Expired 1 hour ago
                .build();
        Client clientExpiredSub = Client.builder()
                .id(clientId)
                .name("Expired Sub Client")
                .apiKey("rk_expired")
                .subscriptionPlan(expiredPlan)
                .build();
        when(clientRepository.findByIdWithSubscriptionPlan(clientId)).thenReturn(Optional.of(clientExpiredSub));
        RateLimitResult result = rateLimitService.tryConsume(clientId);
        assertThat(result.isAllowed()).isFalse();  // ✅ DENY if subscription expired
    }

    @Test
    void tryConsume_denies_when_admin_disables_subscription() {
        SubscriptionPlan disabledPlan = SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .name("Disabled Plan")
                .monthlyLimit(1_000L)
                .active(false)  // ❌ Admin manually disabled (regardless of expiresAt)
                .expiresAt(Instant.now().plusSeconds(86400))  // Would be valid tomorrow
                .build();
        Client clientDisabledSub = Client.builder()
                .id(clientId)
                .name("Admin Disabled Sub Client")
                .apiKey("rk_disabled")
                .subscriptionPlan(disabledPlan)
                .build();
        when(clientRepository.findByIdWithSubscriptionPlan(clientId)).thenReturn(Optional.of(clientDisabledSub));
        RateLimitResult result = rateLimitService.tryConsume(clientId);
        assertThat(result.isAllowed()).isFalse();  // ✅ DENY if admin disabled
    }

    @Test
    void tryConsume_allows_when_subscription_not_yet_expired() {
        SubscriptionPlan futureExpirePlan = SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .name("Active Plan")
                .monthlyLimit(1_000L)
                .expiresAt(Instant.now().plusSeconds(3600))  // ✅ Expires in 1 hour
                .build();
        Client clientFutureExpireSub = Client.builder()
                .id(clientId)
                .name("Future Expire Sub Client")
                .apiKey("rk_future")
                .subscriptionPlan(futureExpirePlan)
                .build();
        when(clientRepository.findByIdWithSubscriptionPlan(clientId)).thenReturn(Optional.of(clientFutureExpireSub));
        when(effectiveLimitResolver.resolve(any(Client.class))).thenReturn(List.of());
        RateLimitResult result = rateLimitService.tryConsume(clientId);
        assertThat(result.isAllowed()).isTrue();  // ✅ ALLOW if subscription not yet expired
    }

    @Test
    void tryConsume_allows_when_subscription_has_no_expiry() {
        SubscriptionPlan perpetualPlan = SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .name("Perpetual Plan")
                .monthlyLimit(1_000L)
                .expiresAt(null)  // ✅ No expiry = never expires
                .build();
        Client clientPerpetualSub = Client.builder()
                .id(clientId)
                .name("Perpetual Sub Client")
                .apiKey("rk_perpetual")
                .subscriptionPlan(perpetualPlan)
                .build();
        when(clientRepository.findByIdWithSubscriptionPlan(clientId)).thenReturn(Optional.of(clientPerpetualSub));
        when(effectiveLimitResolver.resolve(any(Client.class))).thenReturn(List.of());
        RateLimitResult result = rateLimitService.tryConsume(clientId);
        assertThat(result.isAllowed()).isTrue();  // ✅ ALLOW if no expiry set
    }

    @Test
    void tryConsume_allows_when_no_limits() {
        when(clientRepository.findByIdWithSubscriptionPlan(clientId)).thenReturn(Optional.of(client));
        when(effectiveLimitResolver.resolve(any(Client.class))).thenReturn(List.of());
        RateLimitResult result = rateLimitService.tryConsume(clientId);
        assertThat(result.isAllowed()).isTrue();  // ✅ ALLOW if has active subscription but no limits
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryConsume_allows_when_under_limit() {
        when(clientRepository.findByIdWithSubscriptionPlan(clientId)).thenReturn(Optional.of(client));
        when(effectiveLimitResolver.resolve(any(Client.class))).thenReturn(
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
        when(effectiveLimitResolver.resolve(any(Client.class))).thenReturn(
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
