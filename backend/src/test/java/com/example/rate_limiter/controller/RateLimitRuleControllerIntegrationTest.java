package com.example.rate_limiter.controller;

import com.example.rate_limiter.domain.Client;
import com.example.rate_limiter.domain.RateLimitType;
import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.dto.CreateRateLimitRuleRequest;
import com.example.rate_limiter.repository.ClientRepository;
import com.example.rate_limiter.repository.RateLimitRuleRepository;
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

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitRuleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitRuleRepository rateLimitRuleRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    private UUID clientId;

    @BeforeEach
    void setUp() {
        rateLimitRuleRepository.deleteAll();
        clientRepository.deleteAll();
        subscriptionPlanRepository.deleteAll();

        // Create subscription plan
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("Test Plan")
                .monthlyLimit(1000L)
                .active(true)
                .build();
        plan = subscriptionPlanRepository.save(plan);

        // Create client
        Client client = Client.builder()
                .name("Test Client")
                .apiKey("rk_test123")
                .subscriptionPlan(plan)
                .active(true)
                .build();
        client = clientRepository.save(client);
        clientId = client.getId();
    }

    @Test
    void testCreateMonthlyRule_Success() throws Exception {
        // Note: The API only supports GLOBAL rules; per-client limits use subscription plans
        CreateRateLimitRuleRequest request = new CreateRateLimitRuleRequest(
                RateLimitType.GLOBAL,
                5000,
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limitType").value("GLOBAL"))
                .andExpect(jsonPath("$.limitValue").value(5000))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void testCreateWindowRule_Success() throws Exception {
        // Note: The API only supports GLOBAL rules; per-client limits use subscription plans
        CreateRateLimitRuleRequest request = new CreateRateLimitRuleRequest(
                RateLimitType.GLOBAL,
                100,
                null,
                60,
                null
        );

        mockMvc.perform(post("/api/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limitType").value("GLOBAL"))
                .andExpect(jsonPath("$.limitValue").value(100))
                .andExpect(jsonPath("$.globalWindowSeconds").value(60));
    }

    @Test
    void testCreateGlobalRule_Success() throws Exception {
        CreateRateLimitRuleRequest request = new CreateRateLimitRuleRequest(
                RateLimitType.GLOBAL,
                1000000,
                3600,
                3600,
                null
        );

        mockMvc.perform(post("/api/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limitType").value("GLOBAL"))
                .andExpect(jsonPath("$.clientId").doesNotExist());
    }

    @Test
    void testListRules_Success() throws Exception {
        CreateRateLimitRuleRequest rule1 = new CreateRateLimitRuleRequest(
                RateLimitType.GLOBAL,
                5000,
                null,
                null,
                null
        );

        CreateRateLimitRuleRequest rule2 = new CreateRateLimitRuleRequest(
                RateLimitType.GLOBAL,
                100,
                60,
                60,
                null
        );

        mockMvc.perform(post("/api/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rule1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rule2)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/limits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].limitType").value("GLOBAL"))
                .andExpect(jsonPath("$[1].limitType").value("GLOBAL"));
    }

    @Test
    void testListRules_Empty() throws Exception {
        mockMvc.perform(get("/api/limits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testGetRuleById_Success() throws Exception {
        CreateRateLimitRuleRequest request = new CreateRateLimitRuleRequest(
                RateLimitType.GLOBAL,
                5000,
                null,
                null,
                null
        );

        String createResponse = mockMvc.perform(post("/api/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract ID from response
        String ruleId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(get("/api/limits/{id}", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitType").value("GLOBAL"))
                .andExpect(jsonPath("$.limitValue").value(5000));
    }

    @Test
    void testCreateRule_InvalidRequest() throws Exception {
        String invalidJson = "{\"limitType\": \"GLOBAL\"}"; // Missing limitValue

        mockMvc.perform(post("/api/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
