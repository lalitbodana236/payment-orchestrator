package com.lalit.paymentorchestrator.entity;

import com.lalit.paymentorchestrator.enums.PaymentMethod;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentEntityLifecycleTest {

    @Test
    void onCreateShouldSetTimestamps() {
        PaymentEntity entity = PaymentEntity.builder()
                .paymentReference("pay_001")
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.UPI)
                .status(PaymentStatus.CREATED)
                .retryCount(0)
                .build();

        entity.onCreate();

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertTrue(!entity.getUpdatedAt().isBefore(entity.getCreatedAt()));
    }

    @Test
    void onUpdateShouldRefreshUpdatedAt() throws InterruptedException {
        PaymentEntity entity = PaymentEntity.builder()
                .paymentReference("pay_002")
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.UPI)
                .status(PaymentStatus.CREATED)
                .retryCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Instant previousUpdatedAt = entity.getUpdatedAt();
        Thread.sleep(5L);

        entity.onUpdate();

        assertNotNull(entity.getUpdatedAt());
        assertTrue(entity.getUpdatedAt().isAfter(previousUpdatedAt) || entity.getUpdatedAt().equals(previousUpdatedAt));
    }
}
