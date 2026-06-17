package com.lalit.paymentorchestrator.repository;

import com.lalit.paymentorchestrator.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByPaymentReference(String paymentReference);
}
