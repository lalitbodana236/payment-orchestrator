package com.lalit.paymentorchestrator.provider.dto;

import com.lalit.paymentorchestrator.enums.PaymentMethod;

import java.math.BigDecimal;

public record ProviderPaymentRequest(String paymentReference,
                                     BigDecimal amount,
                                     String currency,
                                     PaymentMethod paymentMethod) {
}
