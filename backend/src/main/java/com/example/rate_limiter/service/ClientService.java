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

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private static final String API_KEY_PREFIX = "rk_";

    /**
     * Create a new API client with auto-generated API key and subscription plan.
     * 
     * @param request contains client name and subscription plan ID
     * @return the created client DTO with generated API key
     * @throws IllegalArgumentException if subscription plan not found
     */
    @Transactional
    public ClientDto create(CreateClientRequest request) {
        SubscriptionPlan plan = subscriptionPlanRepository
                .findById(request.getSubscriptionPlanId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Subscription plan not found: " + request.getSubscriptionPlanId()
                ));

        String apiKey = generateApiKey();
        
        Client client = Client.builder()
                .name(request.getName())
                .apiKey(apiKey)
                .subscriptionPlan(plan)
                .active(true)
                .build();

        client = clientRepository.save(client);
        return ClientDto.from(client);
    }

    /**
     * Generate a unique API key with "rk_" prefix.
     */
    private String generateApiKey() {
        return API_KEY_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Retrieve all clients with their subscription plans.
     */
    public List<ClientDto> findAll() {
        return clientRepository.findAllWithSubscriptionPlan()
                .stream()
                .map(ClientDto::from)
                .toList();
    }

    /**
     * Retrieve a specific client by ID.
     * 
     * @throws IllegalArgumentException if client not found
     */
    public ClientDto getById(UUID id) {
        return clientRepository.findByIdWithSubscriptionPlan(id)
                .map(ClientDto::from)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + id));
    }

    /**
     * Retrieve client by API key (used during rate limit checking).
     * 
     * @throws IllegalArgumentException if API key invalid
     */
    public Client getByApiKey(String apiKey) {
        return clientRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new IllegalArgumentException("Invalid API key"));
    }
}
