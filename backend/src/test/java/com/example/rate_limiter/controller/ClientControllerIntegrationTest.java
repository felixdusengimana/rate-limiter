package com.example.rate_limiter.controller;

import com.example.rate_limiter.domain.Client;
import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.dto.CreateClientRequest;
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

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private ClientRepository clientRepository;

    private UUID planId;

    @BeforeEach
    void setUp() {
        clientRepository.deleteAll();
        subscriptionPlanRepository.deleteAll();

        // Create default subscription plan
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("Test Plan")
                .monthlyLimit(1000L)
                .active(true)
                .build();
        plan = subscriptionPlanRepository.save(plan);
        planId = plan.getId();
    }

    @Test
    void testCreateClient_Success() throws Exception {
        CreateClientRequest request = new CreateClientRequest("Acme Corp", planId);

        mockMvc.perform(post("/api/clients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.apiKey").exists())
                .andExpect(jsonPath("$.apiKey").value(startsWith("rk_")))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void testListClients_Success() throws Exception {
        // Create two clients
        Client client1 = Client.builder()
                .name("Client 1")
                .apiKey("rk_key1")
                .subscriptionPlan(subscriptionPlanRepository.findById(planId).get())
                .active(true)
                .build();
        clientRepository.save(client1);

        Client client2 = Client.builder()
                .name("Client 2")
                .apiKey("rk_key2")
                .subscriptionPlan(subscriptionPlanRepository.findById(planId).get())
                .active(true)
                .build();
        clientRepository.save(client2);

        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Client 1", "Client 2")));
    }

    @Test
    void testListClients_Empty() throws Exception {
        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testGetClientById_Success() throws Exception {
        // Create a client
        Client client = Client.builder()
                .name("Test Client")
                .apiKey("rk_test123")
                .subscriptionPlan(subscriptionPlanRepository.findById(planId).get())
                .active(true)
                .build();
        client = clientRepository.save(client);

        mockMvc.perform(get("/api/clients/{id}", client.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Client"))
                .andExpect(jsonPath("$.apiKey").value("rk_test123"));
    }

    @Test
    void testGetClientById_NotFound() throws Exception {
        // Invalid UUID format results in 400 Bad Request
        mockMvc.perform(get("/api/clients/invalid-id"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateClient_InvalidRequest() throws Exception {
        // Missing required name field
        String invalidJson = "{\"subscriptionPlanId\": \"" + planId + "\"}";

        mockMvc.perform(post("/api/clients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateClient_InvalidPlan() throws Exception {
        CreateClientRequest request = new CreateClientRequest("Test", UUID.randomUUID());

        mockMvc.perform(post("/api/clients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }
}
