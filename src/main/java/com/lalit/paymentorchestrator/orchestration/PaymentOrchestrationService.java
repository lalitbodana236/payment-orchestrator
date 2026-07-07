package com.lalit.paymentorchestrator.orchestration;

import com.lalit.paymentorchestrator.dto.PaymentRequest;
import com.lalit.paymentorchestrator.dto.PaymentResponse;
import com.lalit.paymentorchestrator.entity.PaymentEntity;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import com.lalit.paymentorchestrator.exception.PaymentNotFoundException;
import com.lalit.paymentorchestrator.idempotency.IdempotencyService;
import com.lalit.paymentorchestrator.mapper.PaymentMapper;
import com.lalit.paymentorchestrator.metrics.PaymentMetricsRecorder;
import com.lalit.paymentorchestrator.repository.PaymentRepository;
import com.lalit.paymentorchestrator.routing.RoutingStrategy;
import com.lalit.paymentorchestrator.service.PaymentService;
import com.lalit.paymentorchestrator.service.PaymentAutoFinalizeService;
import com.lalit.paymentorchestrator.util.PaymentReferenceGenerator;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

@Service
public class PaymentOrchestrationService implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final RoutingStrategy routingStrategy;
    private final IdempotencyService idempotencyService;
    private final PaymentReferenceGenerator paymentReferenceGenerator;
    private final PaymentMetricsRecorder metricsRecorder;
    private final PaymentAutoFinalizeService autoFinalizeService;
    private final TaskScheduler taskScheduler;
    private final long autoFinalizeDelayMs;

    public PaymentOrchestrationService(PaymentRepository paymentRepository,
                                       PaymentMapper paymentMapper,
                                       RoutingStrategy routingStrategy,
                                       IdempotencyService idempotencyService,
                                       PaymentReferenceGenerator paymentReferenceGenerator,
                                       PaymentMetricsRecorder metricsRecorder,
                                       PaymentAutoFinalizeService autoFinalizeService,
                                       TaskScheduler taskScheduler,
                                       @org.springframework.beans.factory.annotation.Value("${payment.orchestration.auto-finalize-delay-ms:30000}") long autoFinalizeDelayMs) {
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
        this.routingStrategy = routingStrategy;
        this.idempotencyService = idempotencyService;
        this.paymentReferenceGenerator = paymentReferenceGenerator;
        this.metricsRecorder = metricsRecorder;
        this.autoFinalizeService = autoFinalizeService;
        this.taskScheduler = taskScheduler;
        this.autoFinalizeDelayMs = autoFinalizeDelayMs;
    }

    @Override
    @Transactional
    public PaymentResponse createPayment(String idempotencyKey, PaymentRequest request) {
        var sample = metricsRecorder.startSample();
        try {
            PaymentResponse response = idempotencyService.execute(idempotencyKey, request, () -> doCreatePayment(request));
            metricsRecorder.recordApiLatency(sample, "create_payment", "success");
            return response;
        } catch (RuntimeException exception) {
            metricsRecorder.recordApiLatency(sample, "create_payment", "failure");
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String paymentReference) {
        var sample = metricsRecorder.startSample();
        try {
            PaymentResponse response = paymentRepository.findByPaymentReference(paymentReference)
                    .map(paymentMapper::toResponse)
                    .orElseThrow(() -> new PaymentNotFoundException(paymentReference));
            metricsRecorder.recordApiLatency(sample, "get_payment", "success");
            return response;
        } catch (RuntimeException exception) {
            metricsRecorder.recordApiLatency(sample, "get_payment", "failure");
            throw exception;
        }
    }

    protected PaymentResponse doCreatePayment(PaymentRequest request) {
        PaymentEntity paymentEntity = PaymentEntity.builder()
                .paymentReference(paymentReferenceGenerator.nextReference())
                .amount(request.amount())
                .currency(request.currency())
                .paymentMethod(request.paymentMethod())
                .provider(routingStrategy.route(request.paymentMethod()).primaryProvider())
                .status(PaymentStatus.PENDING)
                .retryCount(0)
                .build();

        PaymentEntity saved = paymentRepository.save(paymentEntity);
        scheduleAutoFinalize(saved.getPaymentReference());
        return paymentMapper.toResponse(saved);
    }

    private void scheduleAutoFinalize(String paymentReference) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            taskScheduler.schedule(() -> autoFinalizeService.finalizePayment(paymentReference),
                    Instant.now().plusMillis(autoFinalizeDelayMs));
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskScheduler.schedule(() -> autoFinalizeService.finalizePayment(paymentReference),
                        Instant.now().plusMillis(autoFinalizeDelayMs));
            }
        });
    }
}
