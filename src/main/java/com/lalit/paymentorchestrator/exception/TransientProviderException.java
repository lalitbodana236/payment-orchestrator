package com.lalit.paymentorchestrator.exception;

public class TransientProviderException extends ProviderException {
    public TransientProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
