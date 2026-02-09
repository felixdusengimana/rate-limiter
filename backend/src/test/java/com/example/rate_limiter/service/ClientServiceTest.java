package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.dto.ClientDto;
import com.example.rate_limiter.dto.CreateClientRequest;
import com.example.rate_limiter.repository.ClientRepository;
import com.example.rate_limiter.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ClientService.class)
@ActiveProfiles("test")
class ClientServiceTest {

    @Autowired
    ClientService clientService;

    @Autowired
    ClientRepository clientRepository;

    @Autowired
    SubscriptionPlanRepository subscriptionPlanRepository;

    private UUID planId;

    @BeforeEach
    void setUp() {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("Test Plan")
                .monthlyLimit(1000L)
                .active(true)
                .build();
        plan = subscriptionPlanRepository.save(plan);
        planId = plan.getId();
    }

    @Test
    void create_client_generates_api_key() {
        ClientDto dto = clientService.create(new CreateClientRequest("Acme Corp", planId));
        assertThat(dto.getName()).isEqualTo("Acme Corp");
        assertThat(dto.getApiKey()).startsWith("rk_");
        assertThat(dto.getId()).isNotNull();
        assertThat(dto.isActive()).isTrue();
        assertThat(dto.getSubscriptionPlanId()).isEqualTo(planId);
    }

    @Test
    void findAll_returns_created_clients() {
        clientService.create(new CreateClientRequest("A", planId));
        clientService.create(new CreateClientRequest("B", planId));
        assertThat(clientService.findAll()).hasSize(2);
    }

    @Test
    void getById_returns_client() {
        ClientDto created = clientService.create(new CreateClientRequest("X", planId));
        ClientDto found = clientService.getById(created.getId());
        assertThat(found.getName()).isEqualTo("X");
        assertThat(found.getApiKey()).isEqualTo(created.getApiKey());
    }
}
