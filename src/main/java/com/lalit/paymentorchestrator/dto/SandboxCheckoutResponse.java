package com.lalit.paymentorchestrator.dto;

import com.lalit.paymentorchestrator.enums.PaymentMethod;
import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "SandboxCheckoutResponse", description = "Sandbox checkout session details")
public record SandboxCheckoutResponse(
        String paymentReference,
        PaymentProviderType provider,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        PaymentMethod paymentMethod,
        String checkoutUrl
) {
}
