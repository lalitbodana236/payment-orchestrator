package com.lalit.paymentorchestrator.exception;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String paymentReference) {
        super("Payment not found for reference: " + paymentReference);
    }
}
