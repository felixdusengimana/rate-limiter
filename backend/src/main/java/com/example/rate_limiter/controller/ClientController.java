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

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientDto create(@Valid @RequestBody CreateClientRequest request) {
        return clientService.create(request);
    }

    @GetMapping
    public List<ClientDto> list() {
        return clientService.findAll();
    }

    @GetMapping("/{id}")
    public ClientDto get(@PathVariable UUID id) {
        return clientService.getById(id);
    }
}
