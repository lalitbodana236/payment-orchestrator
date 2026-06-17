package com.lalit.paymentorchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "payment.orchestration")
public record PaymentOrchestrationProperties(Retry retry, Idempotency idempotency, Provider provider) {

    public record Retry(int maxAttempts, long initialIntervalMs, double multiplier, long maxIntervalMs) {
    }

    public record Idempotency(long ttlHours, long lockTtlSeconds, long waitTimeoutMs, long pollIntervalMs) {
    }

    public record Provider(long timeoutMs) {
        public Duration timeout() {
            return Duration.ofMillis(timeoutMs);
        }
    }
}
