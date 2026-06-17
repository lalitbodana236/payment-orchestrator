package com.lalit.paymentorchestrator.provider.connector;

import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.provider.dto.ProviderPaymentRequest;
import com.lalit.paymentorchestrator.provider.dto.ProviderPaymentResponse;

public interface PaymentProviderConnector {
    PaymentProviderType provider();

    ProviderPaymentResponse process(ProviderPaymentRequest request);
}
