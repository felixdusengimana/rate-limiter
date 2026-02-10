package com.example.rate_limiter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateClientRequest {
    @NotBlank
    String name;

    /** Subscription plan ID. Required so the client's usage is limited by their plan. */
    @NotNull
    UUID subscriptionPlanId;
}
