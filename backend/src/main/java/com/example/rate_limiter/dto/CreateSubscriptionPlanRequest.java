package com.example.rate_limiter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Value;

@Value
public class CreateSubscriptionPlanRequest {
    @NotBlank
    String name;

    @NotNull
    @Positive
    Long monthlyLimit;

    /** Optional: max requests per window. If set, windowSeconds is required. */
    Long windowLimit;

    /** Required when windowLimit is set. Window duration in seconds (e.g. 60 = 1 minute). */
    Integer windowSeconds;
}
