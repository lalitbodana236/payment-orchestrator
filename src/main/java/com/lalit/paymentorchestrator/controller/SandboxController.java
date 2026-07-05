package com.lalit.paymentorchestrator.controller;

import com.lalit.paymentorchestrator.dto.ApiResponse;
import com.lalit.paymentorchestrator.dto.PaymentRequest;
import com.lalit.paymentorchestrator.dto.PaymentResponse;
import com.lalit.paymentorchestrator.dto.SandboxCheckoutResponse;
import com.lalit.paymentorchestrator.dto.SandboxWebhookRequest;
import com.lalit.paymentorchestrator.entity.PaymentEntity;
import com.lalit.paymentorchestrator.service.SandboxPaymentService;
import com.lalit.paymentorchestrator.util.CorrelationIdHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/sandbox")
@Tag(name = "Sandbox", description = "Sandbox checkout and webhook simulation APIs")
public class SandboxController {

    private final SandboxPaymentService sandboxPaymentService;

    public SandboxController(SandboxPaymentService sandboxPaymentService) {
        this.sandboxPaymentService = sandboxPaymentService;
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a sandbox payment and return a hosted checkout URL")
    public ApiResponse<SandboxCheckoutResponse> createSandboxPayment(@Valid @RequestBody PaymentRequest request) {
        PaymentEntity paymentEntity = sandboxPaymentService.createSandboxPayment(request);
        String checkoutUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/sandbox/checkout/{paymentReference}")
                .buildAndExpand(paymentEntity.getPaymentReference())
                .toUriString();

        return new ApiResponse<>(
                CorrelationIdHolder.get().orElse("N/A"),
                Instant.now(),
                new SandboxCheckoutResponse(
                        paymentEntity.getPaymentReference(),
                        paymentEntity.getProvider(),
                        paymentEntity.getStatus(),
                        paymentEntity.getAmount(),
                        paymentEntity.getCurrency(),
                        paymentEntity.getPaymentMethod(),
                        checkoutUrl
                )
        );
    }

    @GetMapping(value = "/checkout/{paymentReference}", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Render a hosted sandbox checkout page")
    public void checkoutPage(@PathVariable String paymentReference, HttpServletResponse response) throws IOException {
        PaymentEntity paymentEntity = sandboxPaymentService.getSandboxPayment(paymentReference);
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.getWriter().write(sandboxPaymentService.renderCheckoutPage(paymentEntity));
    }

    @PostMapping("/webhooks/payments/{paymentReference}")
    @Operation(summary = "Simulate provider webhook for a sandbox payment")
    public ApiResponse<PaymentResponse> handleSandboxWebhook(@PathVariable String paymentReference,
                                                             @Valid @RequestBody SandboxWebhookRequest request) {
        PaymentEntity paymentEntity = sandboxPaymentService.completeSandboxPayment(paymentReference, request);
        return new ApiResponse<>(
                CorrelationIdHolder.get().orElse("N/A"),
                Instant.now(),
                new PaymentResponse(
                        paymentEntity.getPaymentReference(),
                        paymentEntity.getAmount(),
                        paymentEntity.getCurrency(),
                        paymentEntity.getPaymentMethod(),
                        paymentEntity.getProvider(),
                        paymentEntity.getStatus(),
                        paymentEntity.getRetryCount(),
                        paymentEntity.getFailureReason(),
                        paymentEntity.getCreatedAt(),
                        paymentEntity.getUpdatedAt()
                )
        );
    }
}
