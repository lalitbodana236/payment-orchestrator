package com.lalit.paymentorchestrator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lalit.paymentorchestrator.dto.PaymentRequest;
import com.lalit.paymentorchestrator.dto.PaymentResponse;
import com.lalit.paymentorchestrator.enums.PaymentMethod;
import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import com.lalit.paymentorchestrator.exception.GlobalExceptionHandler;
import com.lalit.paymentorchestrator.service.PaymentService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final PaymentService paymentService = mock(PaymentService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createPaymentShouldReturnCreatedResponse() throws Exception {
        PaymentResponse response = new PaymentResponse(
                "pay_123",
                new BigDecimal("120.5000"),
                "USD",
                PaymentMethod.UPI,
                PaymentProviderType.PROVIDER_B,
                PaymentStatus.SUCCESS,
                0,
                null,
                Instant.parse("2026-06-19T10:00:00Z"),
                Instant.parse("2026-06-19T10:00:05Z")
        );
        when(paymentService.createPayment(anyString(), any(PaymentRequest.class))).thenReturn(response);

        PaymentRequest request = new PaymentRequest(new BigDecimal("120.50"), "USD", PaymentMethod.UPI);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "idem-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.correlationId").value("N/A"))
                .andExpect(jsonPath("$.data.paymentReference").value("pay_123"))
                .andExpect(jsonPath("$.data.provider").value("PROVIDER_B"));
    }

    @Test
    void getPaymentShouldReturnResponseEnvelope() throws Exception {
        PaymentResponse response = new PaymentResponse(
                "pay_123",
                new BigDecimal("120.5000"),
                "USD",
                PaymentMethod.UPI,
                PaymentProviderType.PROVIDER_B,
                PaymentStatus.SUCCESS,
                0,
                null,
                Instant.parse("2026-06-19T10:00:00Z"),
                Instant.parse("2026-06-19T10:00:05Z")
        );
        when(paymentService.getPayment("pay_123")).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/pay_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentReference").value("pay_123"));
    }

    @Test
    void createPaymentWithoutIdempotencyKeyShouldReturnBadRequest() throws Exception {
        PaymentRequest request = new PaymentRequest(new BigDecimal("120.50"), "USD", PaymentMethod.UPI);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createPaymentWithInvalidBodyShouldReturnBadRequest() throws Exception {
        String invalidJson = """
                {
                  "amount": 0,
                  "currency": "usd",
                  "paymentMethod": null
                }
                """;

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "idem-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
