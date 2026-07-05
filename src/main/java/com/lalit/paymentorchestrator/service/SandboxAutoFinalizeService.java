package com.lalit.paymentorchestrator.service;

import com.lalit.paymentorchestrator.entity.PaymentEntity;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import com.lalit.paymentorchestrator.enums.SandboxPaymentOutcome;
import com.lalit.paymentorchestrator.metrics.PaymentMetricsRecorder;
import com.lalit.paymentorchestrator.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SandboxAutoFinalizeService {

    private final PaymentRepository paymentRepository;
    private final PaymentMetricsRecorder metricsRecorder;

    public SandboxAutoFinalizeService(PaymentRepository paymentRepository, PaymentMetricsRecorder metricsRecorder) {
        this.paymentRepository = paymentRepository;
        this.metricsRecorder = metricsRecorder;
    }

    @Transactional
    public void autoFinalize(String paymentReference) {
        var sample = metricsRecorder.startSample();
        try {
            PaymentEntity paymentEntity = paymentRepository.findByPaymentReference(paymentReference).orElse(null);
            if (paymentEntity == null || paymentEntity.getStatus() != PaymentStatus.PENDING) {
                metricsRecorder.recordApiLatency(sample, "sandbox_auto_finalize", "skipped");
                return;
            }

            SandboxPaymentOutcome outcome = resolveOutcome(paymentReference);
            paymentEntity.setStatus(outcome == SandboxPaymentOutcome.SUCCESS ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
            paymentEntity.setFailureReason(outcome == SandboxPaymentOutcome.SUCCESS ? null : "Sandbox auto-finalized failure");
            paymentRepository.save(paymentEntity);

            metricsRecorder.recordApiLatency(sample, "sandbox_auto_finalize", "success");
            metricsRecorder.incrementPaymentOutcome("sandbox_" + outcome.name().toLowerCase(), paymentEntity.getProvider());
        } catch (RuntimeException exception) {
            metricsRecorder.recordApiLatency(sample, "sandbox_auto_finalize", "failure");
            throw exception;
        }
    }

    private SandboxPaymentOutcome resolveOutcome(String paymentReference) {
        if (paymentReference == null || paymentReference.isBlank()) {
            return SandboxPaymentOutcome.FAILED;
        }

        int lastDigit = Character.digit(paymentReference.charAt(paymentReference.length() - 1), 16);
        return lastDigit >= 0 && lastDigit % 2 == 0 ? SandboxPaymentOutcome.SUCCESS : SandboxPaymentOutcome.FAILED;
    }
}
