package com.example.rate_limiter.config;

import com.example.rate_limiter.domain.Client;
import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.repository.ClientRepository;
import com.example.rate_limiter.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ensures a default subscription plan exists and any client without a plan is assigned to it.
 * Runs after the application starts so the schema and tables exist.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanInitializer implements ApplicationRunner {

    private static final String DEFAULT_PLAN_NAME = "Default";
    private static final long DEFAULT_MONTHLY_LIMIT = 1_000L;

    private final SubscriptionPlanRepository planRepository;
    private final ClientRepository clientRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SubscriptionPlan> plans = planRepository.findAll();
        SubscriptionPlan defaultPlan = plans.stream()
                .filter(p -> DEFAULT_PLAN_NAME.equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElseGet(() -> {
                    SubscriptionPlan p = SubscriptionPlan.builder()
                            .name(DEFAULT_PLAN_NAME)
                            .monthlyLimit(DEFAULT_MONTHLY_LIMIT)
                            .active(true)
                            .build();
                    p = planRepository.save(p);
                    log.info("Created default subscription plan: {} ({} requests/month)", p.getName(), p.getMonthlyLimit());
                    return p;
                });

        List<Client> clientsWithoutPlan = clientRepository.findAll().stream()
                .filter(c -> c.getSubscriptionPlan() == null)
                .toList();
        for (Client c : clientsWithoutPlan) {
            c.setSubscriptionPlan(defaultPlan);
            clientRepository.save(c);
            log.info("Assigned client {} to default subscription plan", c.getName());
        }
    }
}
