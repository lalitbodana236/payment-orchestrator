package com.lalit.paymentorchestrator.provider.dto;

import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.enums.PaymentStatus;

public record ProviderPaymentResponse(PaymentProviderType provider,
                                      PaymentStatus status,
                                      String providerReference) {
}
