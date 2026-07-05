package com.lalit.paymentorchestrator.exception;

import com.lalit.paymentorchestrator.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldMapValidationErrorsToBadRequest() throws Exception {
        MethodParameter parameter = methodParameter("sample", String.class);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "paymentRequest");
        bindingResult.addError(new FieldError("paymentRequest", "amount", "must be greater than 0"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        var response = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
        assertTrue(response.getBody().details().contains("amount: must be greater than 0"));
    }

    @Test
    void shouldMapMissingHeaderToBadRequest() throws Exception {
        MethodParameter parameter = methodParameter("sample", String.class);
        MissingRequestHeaderException exception = new MissingRequestHeaderException("Idempotency-Key", parameter);

        var response = handler.handleMissingHeader(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
    }

    @Test
    void shouldMapNotFoundToNotFound() {
        var response = handler.handleNotFound(new PaymentNotFoundException("pay_123"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("PAYMENT_NOT_FOUND", response.getBody().code());
    }

    @Test
    void shouldMapIdempotencyConflictToConflict() {
        var response = handler.handleIdempotencyConflict(new IdempotencyConflictException("conflict"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("IDEMPOTENCY_CONFLICT", response.getBody().code());
    }

    @Test
    void shouldMapConcurrentRequestToConflict() {
        var response = handler.handleConcurrentRequest(new ConcurrentRequestInProgressException("key-1"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("REQUEST_IN_PROGRESS", response.getBody().code());
    }

    @Test
    void shouldMapProcessingFailureToBadGateway() {
        var response = handler.handleProcessing(new PaymentProcessingException("failed", null));

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("PAYMENT_PROCESSING_FAILED", response.getBody().code());
    }

    @Test
    void shouldMapUnexpectedToInternalServerError() {
        var response = handler.handleUnexpected(new IllegalStateException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().code());
        assertTrue(response.getBody().details().contains("boom"));
    }

    private MethodParameter methodParameter(String methodName, Class<?> parameterType) throws NoSuchMethodException {
        Method method = SampleController.class.getDeclaredMethod(methodName, parameterType);
        return new MethodParameter(method, 0);
    }

    static class SampleController {
        @SuppressWarnings("unused")
        void sample(String value) {
        }
    }
}
