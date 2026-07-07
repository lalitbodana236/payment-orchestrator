package com.lalit.paymentorchestrator.service;

import com.lalit.paymentorchestrator.entity.PaymentEntity;
import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import com.lalit.paymentorchestrator.exception.PaymentNotFoundException;
import com.lalit.paymentorchestrator.exception.PaymentProcessingException;
import com.lalit.paymentorchestrator.metrics.PaymentMetricsRecorder;
import com.lalit.paymentorchestrator.orchestration.ProviderExecutor;
import com.lalit.paymentorchestrator.repository.PaymentRepository;
import com.lalit.paymentorchestrator.routing.PaymentRoute;
import com.lalit.paymentorchestrator.routing.RoutingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAutoFinalizeService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAutoFinalizeService.class);

    private final PaymentRepository paymentRepository;
    private final RoutingStrategy routingStrategy;
    private final ProviderExecutor providerExecutor;
    private final PaymentMetricsRecorder metricsRecorder;

    public PaymentAutoFinalizeService(PaymentRepository paymentRepository,
                                      RoutingStrategy routingStrategy,
                                      ProviderExecutor providerExecutor,
                                      PaymentMetricsRecorder metricsRecorder) {
        this.paymentRepository = paymentRepository;
        this.routingStrategy = routingStrategy;
        this.providerExecutor = providerExecutor;
        this.metricsRecorder = metricsRecorder;
    }

    @Transactional
    public void finalizePayment(String paymentReference) {
        var sample = metricsRecorder.startSample();
        try {
            PaymentEntity paymentEntity = paymentRepository.findByPaymentReference(paymentReference)
                    .orElseThrow(() -> new PaymentNotFoundException(paymentReference));
            if (paymentEntity.getStatus() != PaymentStatus.PENDING) {
                metricsRecorder.recordApiLatency(sample, "auto_finalize_payment", "skipped");
                return;
            }

            PaymentRoute route = routingStrategy.route(paymentEntity.getPaymentMethod());
            for (PaymentProviderType providerType : route.candidates()) {
                try {
                    paymentEntity.setProvider(providerType);
                    paymentEntity.setStatus(PaymentStatus.PROCESSING);
                    paymentRepository.save(paymentEntity);

                    var providerResponse = providerExecutor.execute(paymentEntity, providerType);
                    paymentEntity.setProvider(providerResponse.provider());
                    paymentEntity.setStatus(providerResponse.status());
                    paymentEntity.setFailureReason(null);
                    paymentRepository.save(paymentEntity);
                    metricsRecorder.incrementPaymentOutcome("success", paymentEntity.getProvider());
                    metricsRecorder.recordApiLatency(sample, "auto_finalize_payment", "success");
                    return;
                } catch (RuntimeException exception) {
                    paymentEntity.setProvider(providerType);
                    paymentEntity.setFailureReason(exception.getMessage());
                    paymentRepository.save(paymentEntity);
                    metricsRecorder.incrementRetry(providerType.name(), exception.getClass().getSimpleName());
                    log.warn("Auto-finalize attempt failed. paymentReference={}, provider={}, reason={}",
                            paymentReference, providerType, exception.getMessage());
                }
            }

            paymentEntity.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(paymentEntity);
            metricsRecorder.incrementPaymentOutcome("failure", paymentEntity.getProvider());
            metricsRecorder.recordApiLatency(sample, "auto_finalize_payment", "failure");
            throw new PaymentProcessingException("Auto-finalize payment failed for " + paymentReference, null);
        } catch (RuntimeException exception) {
            metricsRecorder.recordApiLatency(sample, "auto_finalize_payment", "failure");
            throw exception;
        }
    }
}
