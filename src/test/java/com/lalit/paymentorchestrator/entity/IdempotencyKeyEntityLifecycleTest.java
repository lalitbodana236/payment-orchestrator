package com.lalit.paymentorchestrator.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class IdempotencyKeyEntityLifecycleTest {

    @Test
    void onCreateShouldSetCreatedAt() {
        IdempotencyKeyEntity entity = IdempotencyKeyEntity.builder()
                .idempotencyKey("idem-1")
                .requestHash("hash-1")
                .responsePayload("{}")
                .build();

        entity.onCreate();

        assertNotNull(entity.getCreatedAt());
    }
}
