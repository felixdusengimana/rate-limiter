package com.example.rate_limiter.repository;

import com.example.rate_limiter.domain.RateLimitRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RateLimitRuleRepository extends JpaRepository<RateLimitRule, UUID> {

    List<RateLimitRule> findByActiveTrue();
}
