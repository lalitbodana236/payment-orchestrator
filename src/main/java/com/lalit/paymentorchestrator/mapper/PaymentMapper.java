package com.lalit.paymentorchestrator.mapper;

import com.lalit.paymentorchestrator.dto.PaymentResponse;
import com.lalit.paymentorchestrator.entity.PaymentEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(PaymentEntity paymentEntity) {
        return new PaymentResponse(
                paymentEntity.getPaymentReference(),
                paymentEntity.getAmount(),
                paymentEntity.getCurrency(),
                paymentEntity.getPaymentMethod(),
                paymentEntity.getProvider(),
                paymentEntity.getStatus(),
                paymentEntity.getRetryCount(),
                paymentEntity.getFailureReason(),
                paymentEntity.getCreatedAt(),
                paymentEntity.getUpdatedAt()
        );
    }
}
