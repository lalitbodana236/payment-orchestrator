package com.lalit.paymentorchestrator.orchestration;

import com.lalit.paymentorchestrator.dto.PaymentRequest;
import com.lalit.paymentorchestrator.dto.PaymentResponse;
import com.lalit.paymentorchestrator.entity.PaymentEntity;
import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import com.lalit.paymentorchestrator.exception.PaymentNotFoundException;
import com.lalit.paymentorchestrator.exception.PaymentProcessingException;
import com.lalit.paymentorchestrator.idempotency.IdempotencyService;
import com.lalit.paymentorchestrator.mapper.PaymentMapper;
import com.lalit.paymentorchestrator.metrics.PaymentMetricsRecorder;
import com.lalit.paymentorchestrator.provider.dto.ProviderPaymentResponse;
import com.lalit.paymentorchestrator.repository.PaymentRepository;
import com.lalit.paymentorchestrator.routing.PaymentRoute;
import com.lalit.paymentorchestrator.routing.RoutingStrategy;
import com.lalit.paymentorchestrator.service.PaymentService;
import com.lalit.paymentorchestrator.util.PaymentReferenceGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOrchestrationService implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrchestrationService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final RoutingStrategy routingStrategy;
    private final ProviderExecutor providerExecutor;
    private final IdempotencyService idempotencyService;
    private final PaymentReferenceGenerator paymentReferenceGenerator;
    private final PaymentMetricsRecorder metricsRecorder;

    public PaymentOrchestrationService(PaymentRepository paymentRepository,
                                       PaymentMapper paymentMapper,
                                       RoutingStrategy routingStrategy,
                                       ProviderExecutor providerExecutor,
                                       IdempotencyService idempotencyService,
                                       PaymentReferenceGenerator paymentReferenceGenerator,
                                       PaymentMetricsRecorder metricsRecorder) {
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
        this.routingStrategy = routingStrategy;
        this.providerExecutor = providerExecutor;
        this.idempotencyService = idempotencyService;
        this.paymentReferenceGenerator = paymentReferenceGenerator;
        this.metricsRecorder = metricsRecorder;
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
                .status(PaymentStatus.CREATED)
                .retryCount(0)
                .build();

        paymentEntity = paymentRepository.save(paymentEntity);
        PaymentRoute route = routingStrategy.route(request.paymentMethod());
        paymentEntity.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(paymentEntity);

        PaymentProcessingException lastFailure = null;
        for (PaymentProviderType providerType : route.candidates()) {
            if (providerType != route.primaryProvider()) {
                paymentEntity.setStatus(PaymentStatus.RETRYING);
                paymentRepository.save(paymentEntity);
            }
            try {
                ProviderPaymentResponse providerResponse = providerExecutor.execute(paymentEntity, providerType);
                paymentEntity.setProvider(providerResponse.provider());
                paymentEntity.setStatus(providerResponse.status());
                paymentEntity.setFailureReason(null);
                paymentRepository.save(paymentEntity);
                metricsRecorder.incrementPaymentOutcome("success", paymentEntity.getProvider());
                return paymentMapper.toResponse(paymentEntity);
            } catch (RuntimeException exception) {
                lastFailure = new PaymentProcessingException(
                        "Payment execution failed for provider " + providerType + " and payment " + paymentEntity.getPaymentReference(),
                        exception
                );
                paymentEntity.setProvider(providerType);
                paymentEntity.setFailureReason(exception.getMessage());
                paymentRepository.save(paymentEntity);
                metricsRecorder.incrementRetry(providerType.name(), exception.getClass().getSimpleName());
                log.warn("Provider attempt failed. paymentReference={}, provider={}, reason={}",
                        paymentEntity.getPaymentReference(), providerType, exception.getMessage());
            }
        }

        paymentEntity.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(paymentEntity);
        metricsRecorder.incrementPaymentOutcome("failure", paymentEntity.getProvider());
        if (lastFailure == null) {
            throw new PaymentProcessingException("Payment processing failed", null);
        }
        throw lastFailure;
    }
}
