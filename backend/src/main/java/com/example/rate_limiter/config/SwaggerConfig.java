package com.example.rate_limiter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI Configuration for API documentation.
 * 
 * Accessible at: http://localhost:8080/swagger-ui.html
 * API JSON: http://localhost:8080/v3/api-docs
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Rate Limiter API")
                        .version("1.0.0")
                        .description("Distributed API rate limiter for SMS/Email notifications")
                        .contact(new Contact()
                                .name("Felix Dusengimana")
                                .email("phelixdusengimana@gmail.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}
