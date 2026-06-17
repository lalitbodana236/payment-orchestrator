package com.lalit.paymentorchestrator.exception;

public class ConcurrentRequestInProgressException extends RuntimeException {
    public ConcurrentRequestInProgressException(String idempotencyKey) {
        super("A request with idempotency key '" + idempotencyKey + "' is already in progress");
    }
}
