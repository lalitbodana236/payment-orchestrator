package com.lalit.paymentorchestrator.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentReferenceGeneratorTest {

    @Test
    void nextReferenceShouldUsePaymentPrefix() {
        PaymentReferenceGenerator generator = new PaymentReferenceGenerator();

        String reference = generator.nextReference();

        assertTrue(reference.startsWith("pay_"));
        assertTrue(reference.length() > 10);
    }
}
