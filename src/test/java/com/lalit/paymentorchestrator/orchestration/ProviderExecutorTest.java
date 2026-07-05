package com.lalit.paymentorchestrator.orchestration;

import com.lalit.paymentorchestrator.config.PaymentOrchestrationProperties;
import com.lalit.paymentorchestrator.entity.PaymentEntity;
import com.lalit.paymentorchestrator.enums.PaymentMethod;
import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import com.lalit.paymentorchestrator.provider.connector.PaymentProviderConnector;
import com.lalit.paymentorchestrator.provider.dto.ProviderPaymentRequest;
import com.lalit.paymentorchestrator.provider.dto.ProviderPaymentResponse;
import com.lalit.paymentorchestrator.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderExecutorTest {

    @Test
    void executeShouldCallConfiguredConnector() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentProviderConnector connector = mock(PaymentProviderConnector.class);
        when(connector.provider()).thenReturn(PaymentProviderType.PROVIDER_A);
        when(connector.process(any(ProviderPaymentRequest.class))).thenReturn(
                new ProviderPaymentResponse(PaymentProviderType.PROVIDER_A, PaymentStatus.SUCCESS, "prov-1")
        );

        ProviderExecutor executor = new ProviderExecutor(
                new RetryTemplate(),
                CircuitBreakerRegistry.ofDefaults(),
                List.of(connector),
                paymentRepository,
                new PaymentOrchestrationProperties(
                        new PaymentOrchestrationProperties.Retry(1, 100, 1.0, 100),
                        new PaymentOrchestrationProperties.Idempotency(24, 10, 500, 25),
                        new PaymentOrchestrationProperties.Provider(1500)
                )
        );

        PaymentEntity paymentEntity = PaymentEntity.builder()
                .paymentReference("pay_123")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.DEBIT_CARD)
                .status(PaymentStatus.PROCESSING)
                .retryCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ProviderPaymentResponse response = executor.execute(paymentEntity, PaymentProviderType.PROVIDER_A);

        assertEquals(PaymentProviderType.PROVIDER_A, response.provider());
        assertEquals(PaymentStatus.SUCCESS, response.status());
        verify(paymentRepository).save(paymentEntity);
    }

    @Test
    void executeShouldFailWhenConnectorMissing() {
        ProviderExecutor executor = new ProviderExecutor(
                new RetryTemplate(),
                CircuitBreakerRegistry.ofDefaults(),
                List.of(),
                mock(PaymentRepository.class),
                new PaymentOrchestrationProperties(
                        new PaymentOrchestrationProperties.Retry(1, 100, 1.0, 100),
                        new PaymentOrchestrationProperties.Idempotency(24, 10, 500, 25),
                        new PaymentOrchestrationProperties.Provider(1500)
                )
        );

        PaymentEntity paymentEntity = PaymentEntity.builder()
                .paymentReference("pay_123")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.DEBIT_CARD)
                .status(PaymentStatus.PROCESSING)
                .retryCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        assertThrows(IllegalStateException.class, () -> executor.execute(paymentEntity, PaymentProviderType.PROVIDER_A));
    }
}
