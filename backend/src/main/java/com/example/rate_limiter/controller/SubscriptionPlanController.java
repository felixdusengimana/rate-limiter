package com.example.rate_limiter.controller;

import com.example.rate_limiter.dto.CreateSubscriptionPlanRequest;
import com.example.rate_limiter.dto.SubscriptionPlanDto;
import com.example.rate_limiter.service.SubscriptionPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for subscription plan management.
 * Plans define rate limits (monthly + optional per-window) that clients subscribe to.
 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class SubscriptionPlanController {

    private final SubscriptionPlanService planService;

    /**
     * Create a new subscription plan.
     * 
     * @param request contains plan name, monthly limit, and optional window limits
     * @return the created plan DTO
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionPlanDto create(@Valid @RequestBody CreateSubscriptionPlanRequest request) {
        return planService.create(request);
    }

    /**
     * List all subscription plans.
     */
    @GetMapping
    public List<SubscriptionPlanDto> list() {
        return planService.findAll();
    }

    /**
     * Retrieve a specific subscription plan by ID.
     * 
     * @param id the plan UUID
     * @return the plan DTO
     */
    @GetMapping("/{id}")
    public SubscriptionPlanDto get(@PathVariable UUID id) {
        return planService.getById(id);
    }
}
