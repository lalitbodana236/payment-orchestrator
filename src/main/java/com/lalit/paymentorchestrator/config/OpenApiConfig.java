package com.lalit.paymentorchestrator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI paymentOrchestratorOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Payment Orchestration System API")
                .description("Production-style payment orchestration backend with routing, retries, failover, and idempotency.")
                .version("v1")
                .contact(new Contact().name("Lalit"))
                .license(new License().name("Internal Use")));
    }
}
