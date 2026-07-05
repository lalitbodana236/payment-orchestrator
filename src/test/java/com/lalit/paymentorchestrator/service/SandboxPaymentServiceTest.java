package com.lalit.paymentorchestrator.service;

import com.lalit.paymentorchestrator.dto.PaymentRequest;
import com.lalit.paymentorchestrator.dto.SandboxWebhookRequest;
import com.lalit.paymentorchestrator.entity.PaymentEntity;
import com.lalit.paymentorchestrator.enums.PaymentMethod;
import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import com.lalit.paymentorchestrator.enums.SandboxPaymentOutcome;
import com.lalit.paymentorchestrator.metrics.PaymentMetricsRecorder;
import com.lalit.paymentorchestrator.repository.PaymentRepository;
import com.lalit.paymentorchestrator.routing.PaymentRoute;
import com.lalit.paymentorchestrator.routing.RoutingStrategy;
import com.lalit.paymentorchestrator.util.PaymentReferenceGenerator;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SandboxPaymentServiceTest {

    @Test
    void createSandboxPaymentShouldPersistPendingPayment() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        RoutingStrategy routingStrategy = mock(RoutingStrategy.class);
        PaymentReferenceGenerator referenceGenerator = mock(PaymentReferenceGenerator.class);
        PaymentMetricsRecorder metricsRecorder = mock(PaymentMetricsRecorder.class);
        Timer.Sample timerSample = mock(Timer.Sample.class);

        when(metricsRecorder.startSample()).thenReturn(timerSample);
        when(referenceGenerator.nextReference()).thenReturn("pay_123");
        when(routingStrategy.route(PaymentMethod.UPI)).thenReturn(new PaymentRoute(PaymentProviderType.PROVIDER_B, java.util.List.of()));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SandboxPaymentService service = new SandboxPaymentService(paymentRepository, routingStrategy, referenceGenerator, metricsRecorder);

        PaymentEntity paymentEntity = service.createSandboxPayment(new PaymentRequest(new BigDecimal("120.50"), "USD", PaymentMethod.UPI));

        assertEquals("pay_123", paymentEntity.getPaymentReference());
        assertEquals(PaymentStatus.PENDING, paymentEntity.getStatus());
        assertEquals(PaymentProviderType.PROVIDER_B, paymentEntity.getProvider());
        verify(paymentRepository).save(any(PaymentEntity.class));
    }

    @Test
    void completeSandboxPaymentShouldUpdateStatus() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        RoutingStrategy routingStrategy = mock(RoutingStrategy.class);
        PaymentReferenceGenerator referenceGenerator = mock(PaymentReferenceGenerator.class);
        PaymentMetricsRecorder metricsRecorder = mock(PaymentMetricsRecorder.class);
        Timer.Sample timerSample = mock(Timer.Sample.class);

        when(metricsRecorder.startSample()).thenReturn(timerSample);
        PaymentEntity paymentEntity = PaymentEntity.builder()
                .paymentReference("pay_123")
                .amount(new BigDecimal("120.50"))
                .currency("USD")
                .paymentMethod(PaymentMethod.UPI)
                .provider(PaymentProviderType.PROVIDER_B)
                .status(PaymentStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.parse("2026-06-19T10:00:00Z"))
                .updatedAt(Instant.parse("2026-06-19T10:00:05Z"))
                .build();
        when(paymentRepository.findByPaymentReference("pay_123")).thenReturn(Optional.of(paymentEntity));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SandboxPaymentService service = new SandboxPaymentService(paymentRepository, routingStrategy, referenceGenerator, metricsRecorder);

        PaymentEntity completed = service.completeSandboxPayment("pay_123", new SandboxWebhookRequest(SandboxPaymentOutcome.SUCCESS, null));

        assertEquals(PaymentStatus.SUCCESS, completed.getStatus());
        assertTrue(completed.getFailureReason() == null);
    }

    @Test
    void renderCheckoutPageShouldContainPaymentDetails() {
        SandboxPaymentService service = new SandboxPaymentService(mock(PaymentRepository.class), mock(RoutingStrategy.class), mock(PaymentReferenceGenerator.class), mock(PaymentMetricsRecorder.class));
        PaymentEntity paymentEntity = PaymentEntity.builder()
                .paymentReference("pay_123")
                .amount(new BigDecimal("120.50"))
                .currency("USD")
                .paymentMethod(PaymentMethod.UPI)
                .provider(PaymentProviderType.PROVIDER_B)
                .status(PaymentStatus.PENDING)
                .retryCount(0)
                .build();

        String html = service.renderCheckoutPage(paymentEntity);

        assertTrue(html.contains("pay_123"));
        assertTrue(html.contains("Simulate Success"));
    }
}
