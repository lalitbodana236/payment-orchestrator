package com.lalit.paymentorchestrator.orchestration;

import com.lalit.paymentorchestrator.config.PaymentOrchestrationProperties;
import com.lalit.paymentorchestrator.entity.PaymentEntity;
import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.exception.PaymentProcessingException;
import com.lalit.paymentorchestrator.exception.ProviderTimeoutException;
import com.lalit.paymentorchestrator.provider.connector.PaymentProviderConnector;
import com.lalit.paymentorchestrator.provider.dto.ProviderPaymentRequest;
import com.lalit.paymentorchestrator.provider.dto.ProviderPaymentResponse;
import com.lalit.paymentorchestrator.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ProviderExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProviderExecutor.class);

    private final RetryTemplate retryTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Map<PaymentProviderType, PaymentProviderConnector> connectors;
    private final PaymentRepository paymentRepository;
    private final PaymentOrchestrationProperties properties;

    public ProviderExecutor(RetryTemplate retryTemplate,
                            CircuitBreakerRegistry circuitBreakerRegistry,
                            List<PaymentProviderConnector> connectors,
                            PaymentRepository paymentRepository,
                            PaymentOrchestrationProperties properties) {
        this.retryTemplate = retryTemplate;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.paymentRepository = paymentRepository;
        this.properties = properties;
        this.connectors = new EnumMap<>(PaymentProviderType.class);
        connectors.forEach(connector -> this.connectors.put(connector.provider(), connector));
    }

    public ProviderPaymentResponse execute(PaymentEntity paymentEntity, PaymentProviderType providerType) {
        PaymentProviderConnector connector = connectors.get(providerType);
        if (connector == null) {
            throw new IllegalStateException("No connector configured for provider " + providerType);
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(providerType.name());
        try {
            return circuitBreaker.executeSupplier(() ->
                    retryTemplate.execute((RetryCallback<ProviderPaymentResponse, RuntimeException>) context ->
                            invokeWithRetry(paymentEntity, connector, context),
                            (context) -> recoverAfterRetries(context)));
        } catch (CallNotPermittedException exception) {
            throw new PaymentProcessingException("Circuit breaker open for provider " + providerType, exception);
        }
    }

    private ProviderPaymentResponse invokeWithRetry(PaymentEntity paymentEntity,
                                                    PaymentProviderConnector connector,
                                                    RetryContext context) {
        paymentEntity.setRetryCount(context.getRetryCount());
        paymentRepository.save(paymentEntity);
        return invokeConnector(connector, paymentEntity);
    }

    private ProviderPaymentResponse recoverAfterRetries(RetryContext context) {
        Throwable lastThrowable = context.getLastThrowable();
        if (lastThrowable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new PaymentProcessingException("Provider execution failed after retries", lastThrowable);
    }

    private ProviderPaymentResponse invokeConnector(PaymentProviderConnector connector, PaymentEntity paymentEntity) {
        ProviderPaymentRequest request = new ProviderPaymentRequest(
                paymentEntity.getPaymentReference(),
                paymentEntity.getAmount(),
                paymentEntity.getCurrency(),
                paymentEntity.getPaymentMethod()
        );

        CompletableFuture<ProviderPaymentResponse> future = CompletableFuture.supplyAsync(() -> connector.process(request));
        try {
            return future.get(properties.provider().timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new ProviderTimeoutException("Provider " + connector.provider() + " timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentProcessingException("Provider call was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new PaymentProcessingException("Provider call failed", cause);
        }
    }
}
