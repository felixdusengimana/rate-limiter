package com.example.rate_limiter.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class NotificationResponse {
    boolean success;
    String id;
    String channel; // "sms" or "email"
    Instant timestamp;
    String message;
}
