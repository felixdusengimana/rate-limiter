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

    @Transactional
    public RateLimitRuleDto create(CreateRateLimitRuleRequest request) {
        if (request.getLimitType() != RateLimitType.GLOBAL) {
            throw new IllegalArgumentException("Only GLOBAL rate limit rules are supported. Per-client limits come from subscription plans.");
        }
        validate(request);
        RateLimitRule rule = RateLimitRule.builder()
                .limitType(RateLimitType.GLOBAL)
                .limitValue(request.getLimitValue())
                .globalWindowSeconds(request.getGlobalWindowSeconds())
                .active(true)
                .build();
        rule = ruleRepository.save(rule);
        return RateLimitRuleDto.from(rule);
    }

    public List<RateLimitRuleDto> findAll() {
        return ruleRepository.findAll().stream()
                .map(RateLimitRuleDto::from)
                .toList();
    }

    public RateLimitRuleDto getById(UUID id) {
        return ruleRepository.findById(id)
                .map(RateLimitRuleDto::from)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));
    }

    private void validate(CreateRateLimitRuleRequest request) {
        if (request.getLimitValue() < 1) {
            throw new IllegalArgumentException("limitValue must be positive");
        }
    }
}
