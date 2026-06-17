package com.lalit.paymentorchestrator.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "ApiResponse")
public record ApiResponse<T>(String correlationId, Instant timestamp, T data) {
}
