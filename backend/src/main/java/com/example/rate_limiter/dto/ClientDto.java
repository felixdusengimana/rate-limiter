package com.example.rate_limiter.dto;

import com.example.rate_limiter.domain.Client;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class ClientDto {
    UUID id;
    String name;
    String apiKey;
    UUID subscriptionPlanId;
    boolean active;

    public static ClientDto from(Client c) {
        return ClientDto.builder()
                .id(c.getId())
                .name(c.getName())
                .apiKey(c.getApiKey())
                .subscriptionPlanId(c.getSubscriptionPlan() != null ? c.getSubscriptionPlan().getId() : null)
                .active(c.isActive())
                .build();
    }
}
