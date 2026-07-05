package com.lalit.paymentorchestrator.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lalit.paymentorchestrator.config.PaymentOrchestrationProperties;
import com.lalit.paymentorchestrator.dto.PaymentRequest;
import com.lalit.paymentorchestrator.dto.PaymentResponse;
import com.lalit.paymentorchestrator.enums.PaymentMethod;
import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import com.lalit.paymentorchestrator.exception.IdempotencyConflictException;
import com.lalit.paymentorchestrator.repository.IdempotencyKeyRepository;
import com.lalit.paymentorchestrator.util.HashingUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final IdempotencyKeyRepository idempotencyKeyRepository = mock(IdempotencyKeyRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final HashingUtils hashingUtils = new HashingUtils(objectMapper);
    private final PaymentOrchestrationProperties properties = new PaymentOrchestrationProperties(
            new PaymentOrchestrationProperties.Retry(3, 100, 2.0, 2000),
            new PaymentOrchestrationProperties.Idempotency(24, 10, 500, 25),
            new PaymentOrchestrationProperties.Provider(1500)
    );
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.doReturn(valueOperations).when(redisTemplate).opsForValue();
        idempotencyService = new IdempotencyService(redisTemplate, idempotencyKeyRepository, hashingUtils, objectMapper, properties);
    }

    @Test
    void shouldReturnCachedResponseWhenRedisAlreadyHasIt() throws Exception {
        PaymentRequest request = new PaymentRequest(new BigDecimal("100.00"), "USD", PaymentMethod.UPI);
        PaymentResponse response = new PaymentResponse(
                "pay_123",
                new BigDecimal("100.00"),
                "USD",
                PaymentMethod.UPI,
                PaymentProviderType.PROVIDER_B,
                PaymentStatus.SUCCESS,
                0,
                null,
                Instant.parse("2026-06-19T10:00:00Z"),
                Instant.parse("2026-06-19T10:00:05Z")
        );
        String requestHash = hashingUtils.sha256(request);
        when(valueOperations.get("idempotency:hash:idem-1")).thenReturn(requestHash);
        when(valueOperations.get("idempotency:response:idem-1")).thenReturn(objectMapper.writeValueAsString(response));

        PaymentResponse result = idempotencyService.execute("idem-1", request, failingCallback());

        assertEquals(response, result);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void shouldPersistAndReturnFreshResponseWhenRequestIsNew() throws Exception {
        PaymentRequest request = new PaymentRequest(new BigDecimal("100.00"), "USD", PaymentMethod.UPI);
        PaymentResponse response = new PaymentResponse(
                "pay_456",
                new BigDecimal("100.00"),
                "USD",
                PaymentMethod.UPI,
                PaymentProviderType.PROVIDER_B,
                PaymentStatus.SUCCESS,
                0,
                null,
                Instant.parse("2026-06-19T10:00:00Z"),
                Instant.parse("2026-06-19T10:00:05Z")
        );
        when(valueOperations.get("idempotency:hash:idem-2")).thenReturn(null);
        when(valueOperations.get("idempotency:response:idem-2")).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(Boolean.TRUE);
        when(idempotencyKeyRepository.findByIdempotencyKey("idem-2")).thenReturn(Optional.empty());

        PaymentResponse result = idempotencyService.execute("idem-2", request, () -> response);

        assertEquals(response, result);
        verify(idempotencyKeyRepository).save(any());
    }

    @Test
    void shouldRejectDifferentPayloadForSameKey() {
        PaymentRequest request = new PaymentRequest(new BigDecimal("100.00"), "USD", PaymentMethod.UPI);
        String differentHash = "different-hash";
        when(valueOperations.get("idempotency:hash:idem-3")).thenReturn(differentHash);
        when(idempotencyKeyRepository.findByIdempotencyKey("idem-3")).thenReturn(Optional.empty());

        assertThrows(IdempotencyConflictException.class, () ->
                idempotencyService.execute("idem-3", request, failingCallback()));
    }

    private Supplier<PaymentResponse> failingCallback() {
        return () -> {
            throw new IllegalStateException("callback should not run");
        };
    }
}
