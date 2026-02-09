package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.Client;
import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.dto.ClientDto;
import com.example.rate_limiter.dto.CreateClientRequest;
import com.example.rate_limiter.repository.ClientRepository;
import com.example.rate_limiter.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Transactional
    public ClientDto create(CreateClientRequest request) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.getSubscriptionPlanId())
                .orElseThrow(() -> new IllegalArgumentException("Subscription plan not found: " + request.getSubscriptionPlanId()));
        String apiKey = "rk_" + UUID.randomUUID().toString().replace("-", "");
        Client client = Client.builder()
                .name(request.getName())
                .apiKey(apiKey)
                .subscriptionPlan(plan)
                .active(true)
                .build();
        client = clientRepository.save(client);
        return ClientDto.from(client);
    }

    public List<ClientDto> findAll() {
        return clientRepository.findAllWithSubscriptionPlan().stream()
                .map(ClientDto::from)
                .toList();
    }

    public ClientDto getById(UUID id) {
        return clientRepository.findByIdWithSubscriptionPlan(id)
                .map(ClientDto::from)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + id));
    }

    public Client getByApiKey(String apiKey) {
        return clientRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new IllegalArgumentException("Invalid API key"));
    }
}
