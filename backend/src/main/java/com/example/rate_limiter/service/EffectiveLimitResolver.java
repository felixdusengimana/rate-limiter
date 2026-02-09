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
     * Returns all limits that apply to this client:
     * - Plan-based: monthly limit + optional per-window limit
     * - Global: all active GLOBAL rules from repository
     * 
     * @param client the client to resolve limits for
     * @return list of effective limits to enforce
     */
    public List<EffectiveLimit> resolve(Client client) {
        List<EffectiveLimit> limits = new ArrayList<>();

        SubscriptionPlan plan = client.getSubscriptionPlan();
        if (plan.isActive()) {
            addPlanMonthlyLimit(limits, client.getId(), plan);
            addPlanWindowLimit(limits, client.getId(), plan);
        }

        // Add all active global rules
        ruleRepository.findByActiveTrue()
                .stream()
                .filter(r -> r.getLimitType() == RateLimitType.GLOBAL)
                .map(r -> EffectiveLimit.fromGlobalRule(r.getLimitValue(), r.getGlobalWindowSeconds()))
                .forEach(limits::add);

        return limits;
    }

    /**
     * Add plan's monthly limit to the list.
     */
    private void addPlanMonthlyLimit(List<EffectiveLimit> limits, UUID clientId, SubscriptionPlan plan) {
        limits.add(EffectiveLimit.fromPlanMonthly(clientId, plan.getMonthlyLimit()));
    }

    /**
     * Add plan's per-window limit if configured.
     */
    private void addPlanWindowLimit(List<EffectiveLimit> limits, UUID clientId, SubscriptionPlan plan) {
        boolean hasWindowLimit = plan.getWindowLimit() != null 
                && plan.getWindowLimit() > 0 
                && plan.getWindowSeconds() != null 
                && plan.getWindowSeconds() > 0;
        
        if (hasWindowLimit) {
            limits.add(EffectiveLimit.fromPlanWindow(
                    clientId, 
                    plan.getWindowLimit(), 
                    plan.getWindowSeconds()
            ));
        }
    }
}
