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

@RestController
@RequestMapping("/api/limits")
@RequiredArgsConstructor
public class RateLimitRuleController {

    private final RateLimitRuleService ruleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RateLimitRuleDto create(@Valid @RequestBody CreateRateLimitRuleRequest request) {
        return ruleService.create(request);
    }

    @GetMapping
    public List<RateLimitRuleDto> list() {
        return ruleService.findAll();
    }

    @GetMapping("/client/{clientId}")
    public List<RateLimitRuleDto> byClient(@PathVariable UUID clientId) {
        return ruleService.findByClientId(clientId);
    }

    @GetMapping("/{id}")
    public RateLimitRuleDto get(@PathVariable UUID id) {
        return ruleService.getById(id);
    }
}
