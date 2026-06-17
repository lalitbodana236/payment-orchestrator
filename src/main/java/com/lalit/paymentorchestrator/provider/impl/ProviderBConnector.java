package com.lalit.paymentorchestrator.provider.impl;

import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import com.lalit.paymentorchestrator.provider.connector.PaymentProviderConnector;
import com.lalit.paymentorchestrator.provider.dto.ProviderPaymentRequest;
import com.lalit.paymentorchestrator.provider.dto.ProviderPaymentResponse;
import org.springframework.stereotype.Component;

@Component
public class ProviderBConnector implements PaymentProviderConnector {

    @Override
    public PaymentProviderType provider() {
        return PaymentProviderType.PROVIDER_B;
    }

    @Override
    public ProviderPaymentResponse process(ProviderPaymentRequest request) {
        return new ProviderPaymentResponse(provider(), PaymentStatus.SUCCESS, "PROVIDER_B-" + request.paymentReference());
    }
}
