package com.example.rate_limiter.controller;

import com.example.rate_limiter.domain.Client;
import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.dto.NotificationRequest;
import com.example.rate_limiter.repository.ClientRepository;
import com.example.rate_limiter.repository.SubscriptionPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    private String validApiKey;

    @BeforeEach
    void setUp() {
        clientRepository.deleteAll();
        subscriptionPlanRepository.deleteAll();

        // Create subscription plan
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("Test Plan")
                .monthlyLimit(1000L)
                .active(true)
                .build();
        plan = subscriptionPlanRepository.save(plan);

        // Create active client with API key
        Client client = Client.builder()
                .name("Test Client")
                .apiKey("rk_test_notification_123")
                .subscriptionPlan(plan)
                .active(true)
                .build();
        clientRepository.save(client);
        validApiKey = "rk_test_notification_123";
    }

    @Test
    void testSendSms_Success() throws Exception {
        NotificationRequest request = new NotificationRequest(
                "+256701234567",
                "Your verification code is 123456"
        );

        mockMvc.perform(post("/api/notify/sms")
                .header("X-API-Key", validApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.channel").value("sms"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").value("SMS accepted for delivery"));
    }

    @Test
    void testSendEmail_Success() throws Exception {
        NotificationRequest request = new NotificationRequest(
                "user@example.com",
                "Password reset link: https://example.com/reset"
        );

        mockMvc.perform(post("/api/notify/email")
                .header("X-API-Key", validApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.channel").value("email"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.message").value("Email accepted for delivery"));
    }

    @Test
    void testSendSms_MissingRecipient() throws Exception {
        String invalidJson = "{\"message\": \"Test\"}";

        mockMvc.perform(post("/api/notify/sms")
                .header("X-API-Key", validApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSendEmail_MissingMessage() throws Exception {
        String invalidJson = "{\"recipient\": \"test@example.com\"}";

        mockMvc.perform(post("/api/notify/email")
                .header("X-API-Key", validApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSendSms_WithVariousPhoneNumbers() throws Exception {
        String[] phoneNumbers = {
                "+256701234567",
                "+12125551234",
                "+442071234567"
        };

        for (String phone : phoneNumbers) {
            NotificationRequest request = new NotificationRequest(phone, "Test SMS");

            mockMvc.perform(post("/api/notify/sms")
                    .header("X-API-Key", validApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.channel").value("sms"));
        }
    }

    @Test
    void testSendEmail_WithVariousEmailAddresses() throws Exception {
        String[] emails = {
                "user@example.com",
                "admin@company.co.uk",
                "test+tag@domain.io"
        };

        for (String email : emails) {
            NotificationRequest request = new NotificationRequest(email, "Test Email");

            mockMvc.perform(post("/api/notify/email")
                    .header("X-API-Key", validApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.channel").value("email"));
        }
    }

    @Test
    void testSendSms_GeneratesUniqueIds() throws Exception {
        NotificationRequest request1 = new NotificationRequest("+256701234567", "Message 1");
        NotificationRequest request2 = new NotificationRequest("+256701234567", "Message 2");

        String response1 = mockMvc.perform(post("/api/notify/sms")
                .header("X-API-Key", validApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String response2 = mockMvc.perform(post("/api/notify/sms")
                .header("X-API-Key", validApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // IDs should be different
        assert !response1.equals(response2);
    }
}
