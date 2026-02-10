package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.dto.CreateSubscriptionPlanRequest;
import com.example.rate_limiter.dto.SubscriptionPlanDto;
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
@Import(SubscriptionPlanService.class)
@ActiveProfiles("test")
class SubscriptionPlanServiceTest {

    @Autowired
    private SubscriptionPlanService subscriptionPlanService;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @BeforeEach
    void setUp() {
        subscriptionPlanRepository.deleteAll();
    }

    @Test
    void create_subscription_plan_successfully() {
        CreateSubscriptionPlanRequest request = new CreateSubscriptionPlanRequest(
                "Premium Plan", 10000L, 100L, 60);

        SubscriptionPlanDto result = subscriptionPlanService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Premium Plan");
        assertThat(result.getMonthlyLimit()).isEqualTo(10000L);
        assertThat(result.getWindowLimit()).isEqualTo(100L);
        assertThat(result.getWindowSeconds()).isEqualTo(60);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void get_all_subscription_plans() {
        CreateSubscriptionPlanRequest req1 = new CreateSubscriptionPlanRequest(
                "Basic", 1000L, null, null);

        CreateSubscriptionPlanRequest req2 = new CreateSubscriptionPlanRequest(
                "Premium", 10000L, null, null);

        subscriptionPlanService.create(req1);
        subscriptionPlanService.create(req2);

        var plans = subscriptionPlanService.findAll();

        assertThat(plans).hasSize(2);
        assertThat(plans).extracting("name").contains("Basic", "Premium");
    }

    @Test
    void get_subscription_plan_by_id() {
        CreateSubscriptionPlanRequest request = new CreateSubscriptionPlanRequest(
                "Standard Plan", 5000L, null, null);

        SubscriptionPlanDto created = subscriptionPlanService.create(request);
        SubscriptionPlanDto found = subscriptionPlanService.getById(created.getId());

        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Standard Plan");
        assertThat(found.getMonthlyLimit()).isEqualTo(5000L);
    }

    @Test
    void update_subscription_plan() {
        CreateSubscriptionPlanRequest initialRequest = new CreateSubscriptionPlanRequest(
                "Original Plan", 1000L, null, null);

        SubscriptionPlanDto created = subscriptionPlanService.create(initialRequest);
        assertThat(created.getName()).isEqualTo("Original Plan");

        CreateSubscriptionPlanRequest updateRequest = new CreateSubscriptionPlanRequest(
                "Updated Plan", 5000L, 50L, 60);

        SubscriptionPlanDto updated = subscriptionPlanService.update(created.getId(), updateRequest);

        assertThat(updated.getName()).isEqualTo("Updated Plan");
        assertThat(updated.getMonthlyLimit()).isEqualTo(5000L);
        assertThat(updated.getWindowLimit()).isEqualTo(50L);
        assertThat(updated.getWindowSeconds()).isEqualTo(60);
    }

    @Test
    void find_all_returns_all_plans() {
        subscriptionPlanService.create(new CreateSubscriptionPlanRequest(
                "Plan A", 1000L, null, null));
        subscriptionPlanService.create(new CreateSubscriptionPlanRequest(
                "Plan B", 2000L, 100L, 60));
        subscriptionPlanService.create(new CreateSubscriptionPlanRequest(
                "Plan C", 3000L, null, null));

        var allPlans = subscriptionPlanService.findAll();

        assertThat(allPlans).hasSize(3);
        assertThat(allPlans).extracting("name").contains("Plan A", "Plan B", "Plan C");
    }
}
