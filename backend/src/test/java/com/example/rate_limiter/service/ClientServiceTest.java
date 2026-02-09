package com.example.rate_limiter.service;

import com.example.rate_limiter.dto.ClientDto;
import com.example.rate_limiter.dto.CreateClientRequest;
import com.example.rate_limiter.repository.ClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ClientService.class)
@ActiveProfiles("test")
class ClientServiceTest {

    @Autowired
    ClientService clientService;

    @Autowired
    ClientRepository clientRepository;

    @Test
    void create_client_generates_api_key() {
        ClientDto dto = clientService.create(new CreateClientRequest("Acme Corp"));
        assertThat(dto.getName()).isEqualTo("Acme Corp");
        assertThat(dto.getApiKey()).startsWith("rk_");
        assertThat(dto.getId()).isNotNull();
        assertThat(dto.isActive()).isTrue();
    }

    @Test
    void findAll_returns_created_clients() {
        clientService.create(new CreateClientRequest("A"));
        clientService.create(new CreateClientRequest("B"));
        assertThat(clientService.findAll()).hasSize(2);
    }

    @Test
    void getById_returns_client() {
        ClientDto created = clientService.create(new CreateClientRequest("X"));
        ClientDto found = clientService.getById(created.getId());
        assertThat(found.getName()).isEqualTo("X");
        assertThat(found.getApiKey()).isEqualTo(created.getApiKey());
    }
}
