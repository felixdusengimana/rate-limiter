package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.dto.CreateSubscriptionPlanRequest;
import com.example.rate_limiter.dto.SubscriptionPlanDto;
import com.example.rate_limiter.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository planRepository;

    /**
     * Create a new subscription plan with uniqueness check on name.
     * 
     * @param request the creation request with name, monthly limit, and optional window settings
     * @return the created plan DTO
     * @throws IllegalArgumentException if plan name already exists
     */
    @Transactional
    public SubscriptionPlanDto create(CreateSubscriptionPlanRequest request) {
        validateUniqueName(request.getName());
        validatePlanConfiguration(request);
        
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name(request.getName().trim())
                .monthlyLimit(request.getMonthlyLimit())
                .windowLimit(request.getWindowLimit())
                .windowSeconds(request.getWindowSeconds())
                .active(true)
                .build();
        
        plan = planRepository.save(plan);
        return SubscriptionPlanDto.from(plan);
    }

    /**
     * Check that plan name doesn't already exist (case-insensitive).
     */
    private void validateUniqueName(String name) {
        boolean exists = planRepository.findAll()
                .stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(name));
        
        if (exists) {
            throw new IllegalArgumentException(
                    String.format("Plan with name '%s' already exists", name)
            );
        }
    }

    /**
     * Validate plan configuration to ensure window settings are consistent.
     * If windowLimit is set, windowSeconds must also be set.
     * 
     * @throws IllegalArgumentException if window configuration is invalid
     */
    private void validatePlanConfiguration(CreateSubscriptionPlanRequest request) {
        boolean hasWindowLimit = request.getWindowLimit() != null && request.getWindowLimit() > 0;
        boolean hasWindowSeconds = request.getWindowSeconds() != null && request.getWindowSeconds() > 0;

        if (hasWindowLimit && !hasWindowSeconds) {
            throw new IllegalArgumentException(
                    "windowSeconds is required when windowLimit is set. " +
                    "Example: windowLimit=100, windowSeconds=60 (100 requests per minute)"
            );
        }

        if (!hasWindowLimit && hasWindowSeconds) {
            throw new IllegalArgumentException(
                    "windowSeconds cannot be set without windowLimit"
            );
        }
    }

    /**
     * Retrieve all active subscription plans.
     */
    public List<SubscriptionPlanDto> findAll() {
        return planRepository.findAll()
                .stream()
                .map(SubscriptionPlanDto::from)
                .toList();
    }

    /**
     * Retrieve a specific subscription plan by ID.
     * 
     * @throws IllegalArgumentException if plan not found
     */
    public SubscriptionPlanDto getById(UUID id) {
        return planRepository.findById(id)
                .map(SubscriptionPlanDto::from)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));
    }
}
