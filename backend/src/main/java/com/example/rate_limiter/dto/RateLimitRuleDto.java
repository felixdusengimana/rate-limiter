package com.example.rate_limiter.dto;

import com.example.rate_limiter.domain.RateLimitRule;
import com.example.rate_limiter.domain.RateLimitType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class RateLimitRuleDto {
    UUID id;
    RateLimitType limitType;
    long limitValue;
    Integer windowSeconds;
    Integer globalWindowSeconds;
    UUID clientId;
    boolean active;

    public static RateLimitRuleDto from(RateLimitRule r) {
        return RateLimitRuleDto.builder()
                .id(r.getId())
                .limitType(r.getLimitType())
                .limitValue(r.getLimitValue())
                .windowSeconds(r.getWindowSeconds())
                .globalWindowSeconds(r.getGlobalWindowSeconds())
                .clientId(r.getClientId())
                .active(r.isActive())
                .build();
    }
}
