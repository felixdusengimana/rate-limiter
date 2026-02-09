package com.example.rate_limiter.controller;

import com.example.rate_limiter.dto.CreateRateLimitRuleRequest;
import com.example.rate_limiter.dto.RateLimitRuleDto;
import com.example.rate_limiter.service.RateLimitRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for global rate limit rule management.
 * Global rules enforce system-wide limits across all clients.
 * Per-client limits are managed through subscription plans.
 */
@RestController
@RequestMapping("/api/limits")
@RequiredArgsConstructor
public class RateLimitRuleController {

    private final RateLimitRuleService ruleService;

    /**
     * Create a new global rate limit rule.
     * Only GLOBAL rules are supported here; per-client limits use subscription plans.
     * 
     * @param request contains limit value and optional window seconds
     * @return the created rule DTO
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RateLimitRuleDto create(@Valid @RequestBody CreateRateLimitRuleRequest request) {
        return ruleService.create(request);
    }

    /**
     * List all global rate limit rules.
     */
    @GetMapping
    public List<RateLimitRuleDto> list() {
        return ruleService.findAll();
    }

    /**
     * Retrieve a specific rate limit rule by ID.
     * 
     * @param id the rule UUID
     * @return the rule DTO
     */
    @GetMapping("/{id}")
    public RateLimitRuleDto get(@PathVariable UUID id) {
        return ruleService.getById(id);
    }

    /**
     * Update an existing global rate limit rule.
     * 
     * @param id the rule UUID
     * @param request contains updated rule fields
     * @return the updated rule DTO
     */
    @PutMapping("/{id}")
    public RateLimitRuleDto update(@PathVariable UUID id, @Valid @RequestBody CreateRateLimitRuleRequest request) {
        return ruleService.update(id, request);
    }
}
