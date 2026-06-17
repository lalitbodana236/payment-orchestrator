package com.lalit.paymentorchestrator.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lalit.paymentorchestrator.config.PaymentOrchestrationProperties;
import com.lalit.paymentorchestrator.dto.PaymentRequest;
import com.lalit.paymentorchestrator.dto.PaymentResponse;
import com.lalit.paymentorchestrator.entity.IdempotencyKeyEntity;
import com.lalit.paymentorchestrator.exception.ConcurrentRequestInProgressException;
import com.lalit.paymentorchestrator.exception.IdempotencyConflictException;
import com.lalit.paymentorchestrator.repository.IdempotencyKeyRepository;
import com.lalit.paymentorchestrator.util.HashingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final HashingUtils hashingUtils;
    private final ObjectMapper objectMapper;
    private final PaymentOrchestrationProperties properties;

    public IdempotencyService(StringRedisTemplate redisTemplate,
                              IdempotencyKeyRepository idempotencyKeyRepository,
                              HashingUtils hashingUtils,
                              ObjectMapper objectMapper,
                              PaymentOrchestrationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.hashingUtils = hashingUtils;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public PaymentResponse execute(String idempotencyKey, PaymentRequest request, Supplier<PaymentResponse> callback) {
        String requestHash = hashingUtils.sha256(request);
        String lockKey = lockKey(idempotencyKey);
        String responseKey = responseKey(idempotencyKey);
        String hashKey = hashKey(idempotencyKey);
        String lockToken = UUID.randomUUID().toString();

        PaymentResponse cachedResponse = readAndValidateCachedResponse(idempotencyKey, requestHash, responseKey, hashKey);
        if (cachedResponse != null) {
            return cachedResponse;
        }

        boolean lockAcquired = Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockToken, Duration.ofSeconds(properties.idempotency().lockTtlSeconds())));
        if (!lockAcquired) {
            return waitForInFlightRequest(idempotencyKey, requestHash, responseKey, hashKey);
        }

        try {
            validateHash(idempotencyKey, requestHash, hashKey);
            PaymentResponse cachedAfterLock = readAndValidateCachedResponse(idempotencyKey, requestHash, responseKey, hashKey);
            if (cachedAfterLock != null) {
                return cachedAfterLock;
            }

            redisTemplate.opsForValue().set(hashKey, requestHash, Duration.ofHours(properties.idempotency().ttlHours()));
            PaymentResponse response = callback.get();
            persistResponse(idempotencyKey, requestHash, responseKey, response);
            return response;
        } finally {
            releaseLock(lockKey, lockToken);
        }
    }

    private PaymentResponse readAndValidateCachedResponse(String idempotencyKey,
                                                          String requestHash,
                                                          String responseKey,
                                                          String hashKey) {
        validateHash(idempotencyKey, requestHash, hashKey);
        String payload = redisTemplate.opsForValue().get(responseKey);
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, PaymentResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize cached idempotent response", exception);
        }
    }

    private void persistResponse(String idempotencyKey, String requestHash, String responseKey, PaymentResponse response) {
        try {
            String responsePayload = objectMapper.writeValueAsString(response);
            Duration ttl = Duration.ofHours(properties.idempotency().ttlHours());
            redisTemplate.opsForValue().set(responseKey, responsePayload, ttl);
            IdempotencyKeyEntity entity = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseGet(IdempotencyKeyEntity::new);
            entity.setIdempotencyKey(idempotencyKey);
            entity.setRequestHash(requestHash);
            entity.setResponsePayload(responsePayload);
            idempotencyKeyRepository.save(entity);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize idempotent response", exception);
        }
    }

    private void validateHash(String idempotencyKey, String requestHash, String hashKey) {
        String currentHash = Optional.ofNullable(redisTemplate.opsForValue().get(hashKey))
                .or(() -> idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey).map(IdempotencyKeyEntity::getRequestHash))
                .orElse(null);
        if (currentHash != null && !currentHash.equals(requestHash)) {
            throw new IdempotencyConflictException("Idempotency key was previously used with a different request payload");
        }
    }

    private PaymentResponse waitForInFlightRequest(String idempotencyKey,
                                                   String requestHash,
                                                   String responseKey,
                                                   String hashKey) {
        long deadline = System.currentTimeMillis() + properties.idempotency().waitTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            PaymentResponse cached = readAndValidateCachedResponse(idempotencyKey, requestHash, responseKey, hashKey);
            if (cached != null) {
                return cached;
            }
            sleep(properties.idempotency().pollIntervalMs());
        }
        throw new ConcurrentRequestInProgressException(idempotencyKey);
    }

    private void releaseLock(String lockKey, String lockToken) {
        String currentToken = redisTemplate.opsForValue().get(lockKey);
        if (lockToken.equals(currentToken)) {
            redisTemplate.delete(lockKey);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for idempotent response", exception);
        }
    }

    private String lockKey(String idempotencyKey) {
        return "idempotency:lock:" + idempotencyKey;
    }

    private String responseKey(String idempotencyKey) {
        return "idempotency:response:" + idempotencyKey;
    }

    private String hashKey(String idempotencyKey) {
        return "idempotency:hash:" + idempotencyKey;
    }
}
