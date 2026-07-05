package com.lalit.paymentorchestrator.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilityControllerTest {

    @Test
    void shouldExposeOnlyPaymentMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Counter.builder("payment.processed.total")
                .tag("outcome", "success")
                .tag("provider", "PROVIDER_A")
                .register(meterRegistry)
                .increment();
        Timer.builder("payment.api.latency")
                .tag("operation", "create_payment")
                .tag("outcome", "success")
                .register(meterRegistry)
                .record(Duration.ofMillis(25));
        Counter.builder("http.server.requests")
                .register(meterRegistry)
                .increment();

        ObservabilityController controller = new ObservabilityController(meterRegistry);

        var metrics = controller.getApplicationMetrics();

        assertEquals(2, metrics.size());
        assertTrue(metrics.stream().anyMatch(metric -> metric.name().equals("payment.processed.total")));
        assertTrue(metrics.stream().anyMatch(metric -> metric.name().equals("payment.api.latency")));
    }
}
