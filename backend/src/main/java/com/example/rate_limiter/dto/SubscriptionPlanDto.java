package com.example.rate_limiter.dto;

import com.example.rate_limiter.domain.SubscriptionPlan;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class SubscriptionPlanDto {
    UUID id;
    String name;
    long monthlyLimit;
    Long windowLimit;
    Integer windowSeconds;
    boolean active;

    public static SubscriptionPlanDto from(SubscriptionPlan p) {
        return SubscriptionPlanDto.builder()
                .id(p.getId())
                .name(p.getName())
                .monthlyLimit(p.getMonthlyLimit())
                .windowLimit(p.getWindowLimit())
                .windowSeconds(p.getWindowSeconds())
                .active(p.isActive())
                .build();
    }
}
