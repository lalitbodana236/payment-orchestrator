package com.lalit.paymentorchestrator.exception;

public class ProviderException extends RuntimeException {
    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProviderException(String message) {
        super(message);
    }
}
