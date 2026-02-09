package com.example.rate_limiter.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Value;

@Value
public class NotificationRequest {
    @NotBlank
    String recipient;
    @NotBlank
    String message;
}
