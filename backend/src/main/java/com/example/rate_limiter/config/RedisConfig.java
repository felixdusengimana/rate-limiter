package com.example.rate_limiter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class RedisConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            @Value("${spring.data.redis.url:redis://localhost:6379}") String redisUrl) {
        
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        
        // Test connection and log result
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            // Mask password in logs
            String maskedUrl = redisUrl.replaceAll(":[^@]*@", ":****@");
            logger.info("✅ Redis connected successfully! URL: {}", maskedUrl);
        } catch (Exception e) {
            logger.error("❌ Failed to connect to Redis: {}", e.getMessage());
            logger.warn("⚠️ Redis connection failed. Rate limiting will not work properly!");
        }
        
        return redisTemplate;
    }
}

