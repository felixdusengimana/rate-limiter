package com.example.rate_limiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    boolean success;
    String id;
    String channel; // "sms" or "email"
    Instant timestamp;
    String message;
}
