package com.lalit.paymentorchestrator.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class HashingUtils {

    private final ObjectMapper objectMapper;

    public HashingUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String sha256(Object value) {
        try {
            byte[] payload = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte hashByte : hash) {
                builder.append(String.format("%02x", hashByte));
            }
            return builder.toString();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize payload for hashing", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }
}
