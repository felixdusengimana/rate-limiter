package com.example.rate_limiter.service;

import com.example.rate_limiter.dto.NotificationRequest;
import com.example.rate_limiter.dto.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

class NotificationServiceTest {

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService();
    }

    @Test
    void testSendSms_Success() {
        // Arrange
        NotificationRequest request = new NotificationRequest("+256701234567", "Your code is 123456");

        // Act
        NotificationResponse response = notificationService.sendSms(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("sms", response.getChannel());
        assertNotNull(response.getId());
        assertNotNull(response.getTimestamp());
        assertEquals("SMS accepted for delivery", response.getMessage());
    }

    @Test
    void testSendEmail_Success() {
        // Arrange
        NotificationRequest request = new NotificationRequest("user@example.com", "Your verification code is 123456");

        // Act
        NotificationResponse response = notificationService.sendEmail(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("email", response.getChannel());
        assertNotNull(response.getId());
        assertNotNull(response.getTimestamp());
        assertEquals("Email accepted for delivery", response.getMessage());
    }

    @Test
    void testSendSms_GeneratesUniqueIds() {
        // Arrange
        NotificationRequest request1 = new NotificationRequest("+256701234567", "Message 1");
        NotificationRequest request2 = new NotificationRequest("+256701234567", "Message 2");

        // Act
        NotificationResponse response1 = notificationService.sendSms(request1);
        NotificationResponse response2 = notificationService.sendSms(request2);

        // Assert
        assertNotEquals(response1.getId(), response2.getId());
    }

    @Test
    void testSendEmail_WithVariousRecipients() {
        // Test multiple email addresses
        String[] recipients = {"test1@example.com", "test2@example.com", "user+tag@domain.co.uk"};

        for (String recipient : recipients) {
            NotificationRequest request = new NotificationRequest(recipient, "Test message");

            NotificationResponse response = notificationService.sendEmail(request);

            assertTrue(response.isSuccess());
            assertEquals("email", response.getChannel());
        }
    }
}
