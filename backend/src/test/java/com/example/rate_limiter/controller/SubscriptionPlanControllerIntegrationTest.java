package com.example.rate_limiter.controller;

import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.dto.CreateSubscriptionPlanRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubscriptionPlanControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private ClientRepository clientRepository;

    @BeforeEach
    void setUp() {
        clientRepository.deleteAll();
        subscriptionPlanRepository.deleteAll();
    }

    @Test
    void testCreatePlan_BasicSuccess() throws Exception {
        CreateSubscriptionPlanRequest request = new CreateSubscriptionPlanRequest(
                "Basic Plan",
                1000L,
                null,
                null
        );

        mockMvc.perform(post("/api/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Basic Plan"))
                .andExpect(jsonPath("$.monthlyLimit").value(1000))
                .andExpect(jsonPath("$.windowLimit").doesNotExist())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void testCreatePlan_WithWindowLimit() throws Exception {
        CreateSubscriptionPlanRequest request = new CreateSubscriptionPlanRequest(
                "Premium Plan",
                10000L,
                100L,
                60
        );

        mockMvc.perform(post("/api/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Premium Plan"))
                .andExpect(jsonPath("$.monthlyLimit").value(10000))
                .andExpect(jsonPath("$.windowLimit").value(100))
                .andExpect(jsonPath("$.windowSeconds").value(60));
    }

    @Test
    void testListPlans_Success() throws Exception {
        // Create plans
        SubscriptionPlan plan1 = SubscriptionPlan.builder()
                .name("Basic")
                .monthlyLimit(1000L)
                .active(true)
                .build();
        subscriptionPlanRepository.save(plan1);

        SubscriptionPlan plan2 = SubscriptionPlan.builder()
                .name("Premium")
                .monthlyLimit(10000L)
                .windowLimit(100L)
                .windowSeconds(60)
                .active(true)
                .build();
        subscriptionPlanRepository.save(plan2);

        mockMvc.perform(get("/api/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Basic"))
                .andExpect(jsonPath("$[1].name").value("Premium"));
    }

    @Test
    void testListPlans_Empty() throws Exception {
        mockMvc.perform(get("/api/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testGetPlanById_Success() throws Exception {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("Enterprise")
                .monthlyLimit(50000L)
                .active(true)
                .build();
        plan = subscriptionPlanRepository.save(plan);

        mockMvc.perform(get("/api/plans/{id}", plan.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Enterprise"))
                .andExpect(jsonPath("$.monthlyLimit").value(50000));
    }

    @Test
    void testGetPlanById_NotFound() throws Exception {
        // Invalid UUID format results in 400 Bad Request
        mockMvc.perform(get("/api/plans/invalid-id"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreatePlan_DuplicateName() throws Exception {
        // Create first plan
        CreateSubscriptionPlanRequest request1 = new CreateSubscriptionPlanRequest(
                "Duplicate Name",
                1000L,
                null,
                null
        );

        mockMvc.perform(post("/api/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Try to create another with the same name
        CreateSubscriptionPlanRequest request2 = new CreateSubscriptionPlanRequest(
                "Duplicate Name",
                2000L,
                null,
                null
        );

        mockMvc.perform(post("/api/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testUpdatePlan_Success() throws Exception {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("Original Name")
                .monthlyLimit(1000L)
                .active(true)
                .build();
        plan = subscriptionPlanRepository.save(plan);

        CreateSubscriptionPlanRequest updateRequest = new CreateSubscriptionPlanRequest(
                "Updated Name",
                2000L,
                null,
                null
        );

        mockMvc.perform(put("/api/plans/{id}", plan.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.monthlyLimit").value(2000));
    }
}
