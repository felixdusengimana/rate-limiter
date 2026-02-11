package com.example.rate_limiter.controller;

import com.example.rate_limiter.dto.NotificationRequest;
import com.example.rate_limiter.dto.NotificationResponse;
import com.example.rate_limiter.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Protected notification API for sending SMS and email messages.
 * Rate limiting is applied by filter using the X-API-Key header.
 */
@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Send an SMS notification.
     * Requires X-API-Key header with valid client API key.
     * 
     * @param request contains recipient phone and message
     * @return confirmation with message ID and timestamp
     */
    @PostMapping("/sms")
    public NotificationResponse sendSms(@Valid @RequestBody NotificationRequest request) {
        return notificationService.sendSms(request);
    }

    /**
     * Send an email notification.
     * Requires X-API-Key header with valid client API key.
     * 
     * @param request contains recipient email and message
     * @return confirmation with message ID and timestamp
     */
    @PostMapping("/email")
    public NotificationResponse sendEmail(@Valid @RequestBody NotificationRequest request) {
        return notificationService.sendEmail(request);
    }
}
