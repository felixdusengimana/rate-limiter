package com.example.rate_limiter.repository;

import com.example.rate_limiter.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findByApiKey(String apiKey);

    @Query("SELECT c FROM Client c LEFT JOIN FETCH c.subscriptionPlan WHERE c.id = :id")
    Optional<Client> findByIdWithSubscriptionPlan(UUID id);

    @Query("SELECT DISTINCT c FROM Client c LEFT JOIN FETCH c.subscriptionPlan")
    List<Client> findAllWithSubscriptionPlan();

    boolean existsByApiKey(String apiKey);
}
