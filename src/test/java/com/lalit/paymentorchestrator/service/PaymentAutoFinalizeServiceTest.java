package com.lalit.paymentorchestrator.service;

import com.lalit.paymentorchestrator.entity.PaymentEntity;
import com.lalit.paymentorchestrator.enums.PaymentMethod;
import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import com.lalit.paymentorchestrator.exception.PaymentNotFoundException;
import com.lalit.paymentorchestrator.metrics.PaymentMetricsRecorder;
import com.lalit.paymentorchestrator.orchestration.ProviderExecutor;
import com.lalit.paymentorchestrator.repository.PaymentRepository;
import com.lalit.paymentorchestrator.routing.PaymentRoute;
import com.lalit.paymentorchestrator.routing.RoutingStrategy;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAutoFinalizeServiceTest {

    @Test
    void finalizePaymentShouldUpdatePendingPayment() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        RoutingStrategy routingStrategy = mock(RoutingStrategy.class);
        ProviderExecutor providerExecutor = mock(ProviderExecutor.class);
        PaymentMetricsRecorder metricsRecorder = mock(PaymentMetricsRecorder.class);
        Timer.Sample timerSample = mock(Timer.Sample.class);

        when(metricsRecorder.startSample()).thenReturn(timerSample);
        when(routingStrategy.route(PaymentMethod.UPI)).thenReturn(new PaymentRoute(PaymentProviderType.PROVIDER_B, List.of(PaymentProviderType.PROVIDER_B)));
        when(paymentRepository.findByPaymentReference("pay_123")).thenReturn(Optional.of(PaymentEntity.builder()
                .paymentReference("pay_123")
                .amount(new BigDecimal("120.50"))
                .currency("USD")
                .paymentMethod(PaymentMethod.UPI)
                .provider(PaymentProviderType.PROVIDER_B)
                .status(PaymentStatus.PENDING)
                .retryCount(0)
                .build()));
        when(providerExecutor.execute(any(PaymentEntity.class), any())).thenAnswer(invocation -> new com.lalit.paymentorchestrator.provider.dto.ProviderPaymentResponse(
                (PaymentProviderType) invocation.getArgument(1),
                PaymentStatus.SUCCESS,
                "provider-ref-1"
        ));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentAutoFinalizeService service = new PaymentAutoFinalizeService(paymentRepository, routingStrategy, providerExecutor, metricsRecorder);
        service.finalizePayment("pay_123");

        verify(paymentRepository, times(2)).save(any(PaymentEntity.class));
    }

    @Test
    void finalizePaymentShouldThrowWhenMissing() {
        PaymentAutoFinalizeService service = new PaymentAutoFinalizeService(
                mock(PaymentRepository.class),
                mock(RoutingStrategy.class),
                mock(ProviderExecutor.class),
                mock(PaymentMetricsRecorder.class));

        assertThrows(PaymentNotFoundException.class, () -> service.finalizePayment("pay_missing"));
    }
}
