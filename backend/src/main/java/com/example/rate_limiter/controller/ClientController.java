package com.example.rate_limiter.controller;

import com.example.rate_limiter.dto.ClientDto;
import com.example.rate_limiter.dto.CreateClientRequest;
import com.example.rate_limiter.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for client management.
 * Clients represent API consumers that send notifications and are subject to rate limits.
 */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    /**
     * Create a new client with auto-generated API key.
     * 
     * @param request contains client name and subscription plan ID
     * @return the created client with API key
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientDto create(@Valid @RequestBody CreateClientRequest request) {
        return clientService.create(request);
    }

    /**
     * List all clients.
     */
    @GetMapping
    public List<ClientDto> list() {
        return clientService.findAll();
    }

    /**
     * Retrieve a specific client by ID.
     * 
     * @param id the client UUID
     * @return the client DTO
     */
    @GetMapping("/{id}")
    public ClientDto get(@PathVariable UUID id) {
        return clientService.getById(id);
    }
}
