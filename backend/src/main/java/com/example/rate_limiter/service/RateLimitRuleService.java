package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.RateLimitRule;
import com.example.rate_limiter.domain.RateLimitType;
import com.example.rate_limiter.dto.CreateRateLimitRuleRequest;
import com.example.rate_limiter.dto.RateLimitRuleDto;
import com.example.rate_limiter.repository.RateLimitRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RateLimitRuleService {

    private final RateLimitRuleRepository ruleRepository;

    /**
     * Create a new global rate limit rule.
     * Only GLOBAL rules are supported; per-client limits come from subscription plans.
     * 
     * @param request the creation request
     * @return the created rule DTO
     * @throws IllegalArgumentException if limit type is not GLOBAL or validation fails
     */
    @Transactional
    public RateLimitRuleDto create(CreateRateLimitRuleRequest request) {
        validateRuleType(request.getLimitType());
        validateRuleValues(request);
        
        RateLimitRule rule = RateLimitRule.builder()
                .limitType(RateLimitType.GLOBAL)
                .limitValue(request.getLimitValue())
                .globalWindowSeconds(request.getGlobalWindowSeconds())
                .active(true)
                .build();
        
        rule = ruleRepository.save(rule);
        return RateLimitRuleDto.from(rule);
    }

    /**
     * Ensure only GLOBAL rule types are created.
     * Per-client limits should use subscription plans instead.
     */
    private void validateRuleType(RateLimitType limitType) {
        if (limitType != RateLimitType.GLOBAL) {
            throw new IllegalArgumentException(
                    "Only GLOBAL rate limit rules are supported. " +
                    "Per-client limits should use subscription plans."
            );
        }
    }

    /**
     * Validate rule content.
     */
    private void validateRuleValues(CreateRateLimitRuleRequest request) {
        if (request.getLimitValue() < 1) {
            throw new IllegalArgumentException("limitValue must be >= 1");
        }
    }

    /**
     * Retrieve all rate limit rules.
     */
    public List<RateLimitRuleDto> findAll() {
        return ruleRepository.findAll()
                .stream()
                .map(RateLimitRuleDto::from)
                .toList();
    }

    /**
     * Retrieve a specific rule by ID.
     * 
     * @throws IllegalArgumentException if rule not found
     */
    public RateLimitRuleDto getById(UUID id) {
        return ruleRepository.findById(id)
                .map(RateLimitRuleDto::from)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));
    }
}
