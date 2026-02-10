package com.example.rate_limiter.dto;

import com.example.rate_limiter.domain.Client;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
                .subscriptionPlanId(c.getSubscriptionPlan().getId())
                .active(c.isActive())
                .build();
    }
}
