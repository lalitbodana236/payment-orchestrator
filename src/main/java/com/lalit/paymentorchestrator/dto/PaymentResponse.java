package com.lalit.paymentorchestrator.dto;

import com.lalit.paymentorchestrator.enums.PaymentMethod;
import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "PaymentResponse", description = "Payment status and orchestration result")
public record PaymentResponse(
        String paymentReference,
        BigDecimal amount,
        String currency,
        PaymentMethod paymentMethod,
        PaymentProviderType provider,
        PaymentStatus status,
        int retryCount,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
}
