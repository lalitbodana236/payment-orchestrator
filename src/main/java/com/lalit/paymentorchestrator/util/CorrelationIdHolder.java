package com.lalit.paymentorchestrator.util;

import org.slf4j.MDC;

import java.util.Optional;

public final class CorrelationIdHolder {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationIdHolder() {
    }

    public static Optional<String> get() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }
}
