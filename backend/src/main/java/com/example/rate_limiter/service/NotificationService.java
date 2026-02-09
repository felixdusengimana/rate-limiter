package com.example.rate_limiter.service;

import com.example.rate_limiter.dto.NotificationRequest;
import com.example.rate_limiter.dto.NotificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Simulated notification service (SMS/Email). In production would integrate with real providers.
 */
@Service
@Slf4j
public class NotificationService {

    public NotificationResponse sendSms(NotificationRequest request) {
        log.info("SMS sent to {}: {}", request.getRecipient(), request.getMessage());
        return NotificationResponse.builder()
                .success(true)
                .id(UUID.randomUUID().toString())
                .channel("sms")
                .timestamp(Instant.now())
                .message("SMS accepted for delivery")
                .build();
    }

    public NotificationResponse sendEmail(NotificationRequest request) {
        log.info("Email sent to {}: {}", request.getRecipient(), request.getMessage());
        return NotificationResponse.builder()
                .success(true)
                .id(UUID.randomUUID().toString())
                .channel("email")
                .timestamp(Instant.now())
                .message("Email accepted for delivery")
                .build();
    }
}
