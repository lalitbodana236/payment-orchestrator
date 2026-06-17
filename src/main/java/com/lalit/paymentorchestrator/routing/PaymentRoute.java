package com.lalit.paymentorchestrator.routing;

import com.lalit.paymentorchestrator.enums.PaymentProviderType;

import java.util.List;

public record PaymentRoute(PaymentProviderType primaryProvider, List<PaymentProviderType> failoverProviders) {

    public PaymentRoute {
        failoverProviders = List.copyOf(failoverProviders);
    }

    public List<PaymentProviderType> candidates() {
        List<PaymentProviderType> candidates = new java.util.ArrayList<>();
        candidates.add(primaryProvider);
        candidates.addAll(failoverProviders);
        return List.copyOf(candidates);
    }
}
