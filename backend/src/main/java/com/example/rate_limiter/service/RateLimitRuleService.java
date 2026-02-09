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
        validate(request);
        RateLimitRule rule = RateLimitRule.builder()
                .limitType(request.getLimitType())
                .limitValue(request.getLimitValue())
                .windowSeconds(request.getWindowSeconds())
                .globalWindowSeconds(request.getGlobalWindowSeconds())
                .clientId(request.getClientId())
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

    public List<RateLimitRuleDto> findByClientId(UUID clientId) {
        return ruleRepository.findByClientIdAndActiveTrue(clientId).stream()
                .map(RateLimitRuleDto::from)
                .toList();
    }

    public RateLimitRuleDto getById(UUID id) {
        return ruleRepository.findById(id)
                .map(RateLimitRuleDto::from)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));
    }

    private void validate(CreateRateLimitRuleRequest request) {
        if (request.getLimitType() == RateLimitType.WINDOW && (request.getWindowSeconds() == null || request.getWindowSeconds() < 1)) {
            throw new IllegalArgumentException("windowSeconds required for WINDOW type (e.g. 60 for 1 minute)");
        }
        if (request.getLimitType() == RateLimitType.GLOBAL && request.getGlobalWindowSeconds() == null) {
            // GLOBAL monthly: no globalWindowSeconds. GLOBAL window: need globalWindowSeconds
            // We allow GLOBAL without globalWindowSeconds = monthly global
        }
        if ((request.getLimitType() == RateLimitType.WINDOW || request.getLimitType() == RateLimitType.MONTHLY)
                && request.getClientId() == null) {
            throw new IllegalArgumentException("clientId required for WINDOW and MONTHLY types");
        }
    }
}
