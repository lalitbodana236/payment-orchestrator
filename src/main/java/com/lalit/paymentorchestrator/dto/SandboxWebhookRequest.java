package com.lalit.paymentorchestrator.dto;

import com.lalit.paymentorchestrator.enums.SandboxPaymentOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "SandboxWebhookRequest", description = "Request used by the sandbox checkout page to notify payment status")
public record SandboxWebhookRequest(
        @NotNull SandboxPaymentOutcome outcome,
        @Size(max = 255) String failureReason
) {
}
