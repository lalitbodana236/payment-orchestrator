package com.lalit.paymentorchestrator.mapper;

import com.lalit.paymentorchestrator.entity.PaymentEntity;
import com.lalit.paymentorchestrator.enums.PaymentMethod;
import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaymentMapperTest {

    private final PaymentMapper paymentMapper = new PaymentMapper();

    @Test
    void toResponseShouldMapAllFields() {
        Instant createdAt = Instant.parse("2026-06-19T10:15:30Z");
        Instant updatedAt = Instant.parse("2026-06-19T10:20:30Z");
        PaymentEntity entity = PaymentEntity.builder()
                .paymentReference("pay_123")
                .amount(new BigDecimal("250.0000"))
                .currency("INR")
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .provider(PaymentProviderType.PROVIDER_A)
                .status(PaymentStatus.SUCCESS)
                .retryCount(2)
                .failureReason(null)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        var response = paymentMapper.toResponse(entity);

        assertEquals("pay_123", response.paymentReference());
        assertEquals(new BigDecimal("250.0000"), response.amount());
        assertEquals("INR", response.currency());
        assertEquals(PaymentMethod.CREDIT_CARD, response.paymentMethod());
        assertEquals(PaymentProviderType.PROVIDER_A, response.provider());
        assertEquals(PaymentStatus.SUCCESS, response.status());
        assertEquals(2, response.retryCount());
        assertNull(response.failureReason());
        assertEquals(createdAt, response.createdAt());
        assertEquals(updatedAt, response.updatedAt());
    }
}
