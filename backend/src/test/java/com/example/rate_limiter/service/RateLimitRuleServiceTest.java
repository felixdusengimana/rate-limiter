package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.*;
import com.example.rate_limiter.dto.CreateRateLimitRuleRequest;
import com.example.rate_limiter.dto.RateLimitRuleDto;
import com.example.rate_limiter.repository.ClientRepository;
import com.example.rate_limiter.repository.RateLimitRuleRepository;
import com.example.rate_limiter.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({RateLimitRuleService.class, ClientService.class, SubscriptionPlanService.class})
@ActiveProfiles("test")
class RateLimitRuleServiceTest {

    @Autowired
    private RateLimitRuleService rateLimitRuleService;

    @Autowired
    private RateLimitRuleRepository rateLimitRuleRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    private UUID clientId;
    private UUID planId;

    @BeforeEach
    void setUp() {
        // Create a subscription plan
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("Test Plan")
                .monthlyLimit(1000L)
                .active(true)
                .build();
        plan = subscriptionPlanRepository.save(plan);
        planId = plan.getId();

        // Create a client
        Client client = Client.builder()
                .name("Test Client")
                .apiKey("rk_test_key")
                .subscriptionPlan(plan)
                .active(true)
                .build();
        client = clientRepository.save(client);
        clientId = client.getId();

        rateLimitRuleRepository.deleteAll();
    }

    @Test
    void create_monthly_rate_limit_rule() {
        CreateRateLimitRuleRequest request = new CreateRateLimitRuleRequest(
                RateLimitType.GLOBAL, 5000, null, null, null);

        RateLimitRuleDto result = rateLimitRuleService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getLimitType()).isEqualTo(RateLimitType.GLOBAL);
        assertThat(result.getLimitValue()).isEqualTo(5000);
    }

    @Test
    void create_window_based_rate_limit_rule() {
        CreateRateLimitRuleRequest request = new CreateRateLimitRuleRequest(
                RateLimitType.GLOBAL, 100, null, 60, null);

        RateLimitRuleDto result = rateLimitRuleService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getLimitType()).isEqualTo(RateLimitType.GLOBAL);
        assertThat(result.getLimitValue()).isEqualTo(100);
        assertThat(result.getGlobalWindowSeconds()).isEqualTo(60);
    }

    @Test
    void create_global_rate_limit_rule() {
        CreateRateLimitRuleRequest request = new CreateRateLimitRuleRequest(
                RateLimitType.GLOBAL, 1000000, null, null, null);

        RateLimitRuleDto result = rateLimitRuleService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getLimitType()).isEqualTo(RateLimitType.GLOBAL);
    }

    @Test
    void get_all_rules() {
        CreateRateLimitRuleRequest rule1 = new CreateRateLimitRuleRequest(
                RateLimitType.GLOBAL, 5000, null, null, null);

        CreateRateLimitRuleRequest rule2 = new CreateRateLimitRuleRequest(
                RateLimitType.GLOBAL, 100, null, 60, null);

        rateLimitRuleService.create(rule1);
        rateLimitRuleService.create(rule2);

        var rules = rateLimitRuleService.findAll();

        assertThat(rules).hasSize(2);
        assertThat(rules).extracting("limitType").containsOnly(RateLimitType.GLOBAL);
    }

    @Test
    void get_rule_by_id() {
        CreateRateLimitRuleRequest request = new CreateRateLimitRuleRequest(
                RateLimitType.GLOBAL, 5000, null, null, null);

        RateLimitRuleDto created = rateLimitRuleService.create(request);
        RateLimitRuleDto found = rateLimitRuleService.getById(created.getId());

        assertThat(found).isNotNull();
        assertThat(found.getLimitValue()).isEqualTo(5000);
    }

    @Test
    void update_rate_limit_rule() {
        CreateRateLimitRuleRequest initialRequest = new CreateRateLimitRuleRequest(
                RateLimitType.GLOBAL, 1000, null, null, null);

        RateLimitRuleDto created = rateLimitRuleService.create(initialRequest);

        CreateRateLimitRuleRequest updateRequest = new CreateRateLimitRuleRequest(
                RateLimitType.GLOBAL, 2000, null, 120, null);

        RateLimitRuleDto updated = rateLimitRuleService.update(created.getId(), updateRequest);

        assertThat(updated.getLimitValue()).isEqualTo(2000);
        assertThat(updated.getGlobalWindowSeconds()).isEqualTo(120);
    }
}
