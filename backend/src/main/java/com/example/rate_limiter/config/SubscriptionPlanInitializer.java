package com.example.rate_limiter.config;

import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ensures a default subscription plan exists on first run so the app has at least one plan to assign to new clients.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanInitializer implements ApplicationRunner {

    private static final String DEFAULT_PLAN_NAME = "Default";
    private static final long DEFAULT_MONTHLY_LIMIT = 1_000L;

    private final SubscriptionPlanRepository planRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SubscriptionPlan> plans = planRepository.findAll();
        if (plans.stream().noneMatch(p -> DEFAULT_PLAN_NAME.equalsIgnoreCase(p.getName()))) {
            SubscriptionPlan p = SubscriptionPlan.builder()
                    .name(DEFAULT_PLAN_NAME)
                    .monthlyLimit(DEFAULT_MONTHLY_LIMIT)
                    .active(true)
                    .build();
            planRepository.save(p);
            log.info("Created default subscription plan: {} ({} requests/month)", p.getName(), p.getMonthlyLimit());
        }
    }
}
