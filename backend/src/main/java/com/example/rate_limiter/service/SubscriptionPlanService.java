package com.example.rate_limiter.service;

import com.example.rate_limiter.domain.SubscriptionPlan;
import com.example.rate_limiter.dto.CreateSubscriptionPlanRequest;
import com.example.rate_limiter.dto.SubscriptionPlanDto;
import com.example.rate_limiter.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository planRepository;

    @Transactional
    public SubscriptionPlanDto create(CreateSubscriptionPlanRequest request) {
        if (planRepository.findAll().stream().anyMatch(p -> p.getName().equalsIgnoreCase(request.getName()))) {
            throw new IllegalArgumentException("Plan with name '" + request.getName() + "' already exists");
        }
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name(request.getName().trim())
                .monthlyLimit(request.getMonthlyLimit())
                .windowLimit(request.getWindowLimit())
                .windowSeconds(request.getWindowSeconds())
                .active(true)
                .build();
        plan = planRepository.save(plan);
        return SubscriptionPlanDto.from(plan);
    }

    public List<SubscriptionPlanDto> findAll() {
        return StreamSupport.stream(planRepository.findAll().spliterator(), false)
                .map(SubscriptionPlanDto::from)
                .toList();
    }

    public SubscriptionPlanDto getById(UUID id) {
        return planRepository.findById(id)
                .map(SubscriptionPlanDto::from)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));
    }
}
