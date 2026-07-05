package com.lalit.paymentorchestrator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lalit.paymentorchestrator.dto.PaymentRequest;
import com.lalit.paymentorchestrator.dto.PaymentResponse;
import com.lalit.paymentorchestrator.dto.SandboxCheckoutResponse;
import com.lalit.paymentorchestrator.dto.SandboxWebhookRequest;
import com.lalit.paymentorchestrator.entity.PaymentEntity;
import com.lalit.paymentorchestrator.enums.PaymentMethod;
import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import com.lalit.paymentorchestrator.enums.SandboxPaymentOutcome;
import com.lalit.paymentorchestrator.exception.GlobalExceptionHandler;
import com.lalit.paymentorchestrator.service.SandboxPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SandboxControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SandboxPaymentService sandboxPaymentService = mock(SandboxPaymentService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SandboxController(sandboxPaymentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createSandboxPaymentShouldReturnCheckoutUrl() throws Exception {
        PaymentEntity paymentEntity = PaymentEntity.builder()
                .paymentReference("pay_123")
                .amount(new BigDecimal("120.50"))
                .currency("USD")
                .paymentMethod(PaymentMethod.UPI)
                .provider(PaymentProviderType.PROVIDER_B)
                .status(PaymentStatus.PENDING)
                .retryCount(0)
                .build();
        when(sandboxPaymentService.createSandboxPayment(any(PaymentRequest.class))).thenReturn(paymentEntity);

        PaymentRequest request = new PaymentRequest(new BigDecimal("120.50"), "USD", PaymentMethod.UPI);

        mockMvc.perform(post("/api/v1/sandbox/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentReference").value("pay_123"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.checkoutUrl").exists());
    }

    @Test
    void checkoutPageShouldReturnHtml() throws Exception {
        PaymentEntity paymentEntity = PaymentEntity.builder()
                .paymentReference("pay_123")
                .amount(new BigDecimal("120.50"))
                .currency("USD")
                .paymentMethod(PaymentMethod.UPI)
                .provider(PaymentProviderType.PROVIDER_B)
                .status(PaymentStatus.PENDING)
                .retryCount(0)
                .build();
        when(sandboxPaymentService.getSandboxPayment("pay_123")).thenReturn(paymentEntity);
        when(sandboxPaymentService.renderCheckoutPage(paymentEntity)).thenReturn("<html>checkout</html>");

        mockMvc.perform(get("/api/v1/sandbox/checkout/pay_123"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string("<html>checkout</html>"));
    }

    @Test
    void sandboxWebhookShouldReturnUpdatedPayment() throws Exception {
        PaymentEntity paymentEntity = PaymentEntity.builder()
                .paymentReference("pay_123")
                .amount(new BigDecimal("120.50"))
                .currency("USD")
                .paymentMethod(PaymentMethod.UPI)
                .provider(PaymentProviderType.PROVIDER_B)
                .status(PaymentStatus.SUCCESS)
                .retryCount(0)
                .createdAt(Instant.parse("2026-06-19T10:00:00Z"))
                .updatedAt(Instant.parse("2026-06-19T10:00:05Z"))
                .build();
        when(sandboxPaymentService.completeSandboxPayment(anyString(), any(SandboxWebhookRequest.class))).thenReturn(paymentEntity);

        SandboxWebhookRequest request = new SandboxWebhookRequest(SandboxPaymentOutcome.SUCCESS, null);

        mockMvc.perform(post("/api/v1/sandbox/webhooks/payments/pay_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentReference").value("pay_123"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }
}
