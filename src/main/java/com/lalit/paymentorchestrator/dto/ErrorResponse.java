package com.lalit.paymentorchestrator.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "ErrorResponse")
public record ErrorResponse(String correlationId,
                            Instant timestamp,
                            String code,
                            String message,
                            List<String> details) {
}
