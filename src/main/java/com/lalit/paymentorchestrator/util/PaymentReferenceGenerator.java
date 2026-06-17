package com.lalit.paymentorchestrator.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentReferenceGenerator {

    public String nextReference() {
        return "pay_" + UUID.randomUUID().toString().replace("-", "");
    }
}
