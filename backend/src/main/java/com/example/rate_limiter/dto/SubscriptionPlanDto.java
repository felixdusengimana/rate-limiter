package com.example.rate_limiter.dto;

import com.example.rate_limiter.domain.SubscriptionPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
