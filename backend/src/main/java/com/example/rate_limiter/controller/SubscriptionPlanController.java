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

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class SubscriptionPlanController {

    private final SubscriptionPlanService planService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionPlanDto create(@Valid @RequestBody CreateSubscriptionPlanRequest request) {
        return planService.create(request);
    }

    @GetMapping
    public List<SubscriptionPlanDto> list() {
        return planService.findAll();
    }

    @GetMapping("/{id}")
    public SubscriptionPlanDto get(@PathVariable UUID id) {
        return planService.getById(id);
    }
}
