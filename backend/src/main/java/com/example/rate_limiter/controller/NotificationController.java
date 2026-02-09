package com.example.rate_limiter.controller;

import com.example.rate_limiter.dto.NotificationRequest;
import com.example.rate_limiter.dto.NotificationResponse;
import com.example.rate_limiter.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Protected notification API. Rate limiting is applied by filter using X-API-Key.
 */
@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/sms")
    public NotificationResponse sendSms(@Valid @RequestBody NotificationRequest request) {
        return notificationService.sendSms(request);
    }

    @PostMapping("/email")
    public NotificationResponse sendEmail(@Valid @RequestBody NotificationRequest request) {
        return notificationService.sendEmail(request);
    }
}
