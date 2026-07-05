package com.lalit.paymentorchestrator.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lalit.paymentorchestrator.dto.PaymentRequest;
import com.lalit.paymentorchestrator.enums.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HashingUtilsTest {

    private final HashingUtils hashingUtils = new HashingUtils(new ObjectMapper());

    @Test
    void sha256ShouldBeDeterministicForSamePayload() {
        PaymentRequest request = new PaymentRequest(new BigDecimal("120.50"), "USD", PaymentMethod.UPI);

        String firstHash = hashingUtils.sha256(request);
        String secondHash = hashingUtils.sha256(request);

        assertEquals(firstHash, secondHash);
    }

    @Test
    void sha256ShouldChangeWhenPayloadChanges() {
        PaymentRequest firstRequest = new PaymentRequest(new BigDecimal("120.50"), "USD", PaymentMethod.UPI);
        PaymentRequest secondRequest = new PaymentRequest(new BigDecimal("121.50"), "USD", PaymentMethod.UPI);

        assertNotEquals(hashingUtils.sha256(firstRequest), hashingUtils.sha256(secondRequest));
    }
}
