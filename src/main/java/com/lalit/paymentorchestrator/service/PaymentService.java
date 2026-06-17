package com.lalit.paymentorchestrator.service;

import com.lalit.paymentorchestrator.dto.PaymentRequest;
import com.lalit.paymentorchestrator.dto.PaymentResponse;

public interface PaymentService {
    PaymentResponse createPayment(String idempotencyKey, PaymentRequest request);

    PaymentResponse getPayment(String paymentReference);
}
