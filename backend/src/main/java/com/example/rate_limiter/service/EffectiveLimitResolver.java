package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.Client;
import com.example.rate_limiter.domain.RateLimitRule;
import com.example.rate_limiter.domain.RateLimitType;
import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.repository.RateLimitRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Resolves effective rate limits for a client: from their subscription plan (monthly + optional window)
 * plus global rules from the rule repository.
 */
@Service
@RequiredArgsConstructor
public class EffectiveLimitResolver {

    private final RateLimitRuleRepository ruleRepository;

    /**
     * Returns all limits that apply to this client: plan-based (monthly, optional window) + global rules.
     * If client has no subscription plan, only global rules are returned (client has no per-client limit until assigned a plan).
     */
    public List<EffectiveLimit> resolve(Client client) {
        UUID clientId = client.getId();
        List<EffectiveLimit> out = new ArrayList<>();

        SubscriptionPlan plan = client.getSubscriptionPlan();
        if (plan != null && plan.isActive()) {
            out.add(EffectiveLimit.fromPlanMonthly(clientId, plan.getMonthlyLimit()));
            if (plan.getWindowLimit() != null && plan.getWindowLimit() > 0 && plan.getWindowSeconds() != null && plan.getWindowSeconds() > 0) {
                out.add(EffectiveLimit.fromPlanWindow(clientId, plan.getWindowLimit(), plan.getWindowSeconds()));
            }
        }

        ruleRepository.findByActiveTrue().stream()
                .filter(r -> r.getLimitType() == RateLimitType.GLOBAL)
                .forEach(r -> out.add(EffectiveLimit.fromGlobalRule(r.getLimitValue(), r.getGlobalWindowSeconds())));

        return out;
    }
}
