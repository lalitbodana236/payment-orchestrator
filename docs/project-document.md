# Project Document

## 1. What this project is

This is a payment orchestration backend built in Spring Boot. It receives payment requests, routes them to the correct payment provider, stores the payment state in MySQL, and protects against duplicate requests with idempotency.

It is designed to look and behave like a real fintech backend rather than a throwaway demo.

## 2. Tech stack

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Spring Data Redis
- Spring Retry
- Resilience4j
- Micrometer / Prometheus
- OpenAPI / Swagger
- MySQL
- Redis

## 3. Package structure

- `com.lalit.paymentorchestrator.controller`: REST endpoints
- `com.lalit.paymentorchestrator.orchestration`: main payment workflow
- `com.lalit.paymentorchestrator.idempotency`: duplicate request handling
- `com.lalit.paymentorchestrator.routing`: provider selection rules
- `com.lalit.paymentorchestrator.provider`: provider connectors and DTOs
- `com.lalit.paymentorchestrator.entity`: JPA entities
- `com.lalit.paymentorchestrator.repository`: database access
- `com.lalit.paymentorchestrator.dto`: API request/response models
- `com.lalit.paymentorchestrator.exception`: error handling
- `com.lalit.paymentorchestrator.config`: Spring configuration
- `com.lalit.paymentorchestrator.util`: helper utilities

## 4. Class-by-class explanation

### Application bootstrap

- `PaymentOrchestratorApplication`: starts the Spring Boot application and enables configuration binding for orchestration properties.

### Configuration

- `PaymentOrchestrationProperties`: central configuration for provider timeout, retry policy, and idempotency timing.
- `OpenApiConfig`: defines API metadata shown in Swagger UI.
- `RedisConfig`: configures Redis serializers for string keys and JSON values.
- `RetryConfig`: builds the retry template used for transient provider failures.

### REST layer

- `PaymentController`: exposes `POST /api/v1/payments` and `GET /api/v1/payments/{paymentReference}`.

### DTO layer

- `PaymentRequest`: validated create-payment request payload.
- `PaymentResponse`: final payment status returned to clients.
- `ApiResponse`: response wrapper that includes correlation ID and timestamp.
- `ErrorResponse`: standardized error payload for validation and runtime failures.

### Entity layer

- `PaymentEntity`: persistent payment record, including status, retry count, provider, and timestamps.
- `IdempotencyKeyEntity`: stores the request hash and cached response for duplicate-request protection.

### Repository layer

- `PaymentRepository`: reads and writes payments by payment reference.
- `IdempotencyKeyRepository`: stores idempotency records by key.

### Routing layer

- `RoutingStrategy`: chooses provider paths for a payment method.
- `PaymentRoutingStrategy`: maps `CARD -> PROVIDER_A` first and `UPI -> PROVIDER_B` first.
- `PaymentRoute`: keeps the primary provider and fallback candidates together.

### Provider layer

- `PaymentProviderConnector`: common interface for all provider implementations.
- `ProviderAConnector`: provider A implementation.
- `ProviderBConnector`: provider B implementation.
- `ProviderPaymentRequest`: request sent to providers.
- `ProviderPaymentResponse`: response returned by providers.

### Orchestration layer

- `PaymentService`: service contract used by the controller.
- `PaymentOrchestrationService`: main workflow for create and fetch operations.
- `ProviderExecutor`: wraps provider calls with retry, timeout, and circuit breaker protection.
- `PaymentMapper`: converts database entities into API responses.
- `PaymentMetricsRecorder`: tracks latency, outcomes, and retry counts.
- `IdempotencyService`: enforces duplicate-request behavior and caches successful responses.

### Utility layer

- `CorrelationIdFilter`: ensures every request has a correlation ID.
- `CorrelationIdHolder`: reads the current correlation ID from MDC.
- `HashingUtils`: creates SHA-256 hashes for request comparison.
- `PaymentReferenceGenerator`: creates unique payment references.

### Exception layer

- `GlobalExceptionHandler`: converts exceptions into stable API errors.
- `PaymentNotFoundException`: thrown when a payment reference is missing.
- `IdempotencyConflictException`: thrown when the same idempotency key is reused with a different payload.
- `ConcurrentRequestInProgressException`: thrown when the same idempotency key is already active.
- `PaymentProcessingException`: generic orchestration failure.
- `ProviderException` and subclasses: represent provider-side failures and retryable conditions.

## 5. End-to-end request flow

### Create payment

1. Client calls `POST /api/v1/payments` with an `Idempotency-Key`.
2. `PaymentController` wraps the request in `ApiResponse` and passes it to `PaymentService`.
3. `PaymentOrchestrationService` starts a latency sample for metrics.
4. `IdempotencyService` hashes the request body and checks Redis plus MySQL for a prior result.
5. If a cached response exists, the service returns it immediately.
6. If another request is already processing the same key, the request waits for the cached response or times out with a conflict.
7. If the request is new, the service creates a `PaymentEntity` with status `CREATED`.
8. `PaymentRoutingStrategy` chooses the provider order.
9. `ProviderExecutor` calls the primary provider first and retries transient failures using the configured retry policy.
10. If the primary provider fails, the orchestration layer falls back to the next provider in the route.
11. On success, the entity is updated to `SUCCESS` and the response is cached for future duplicate requests.
12. On repeated failure, the payment is marked `FAILED` and a structured error is returned.

### Fetch payment

1. Client calls `GET /api/v1/payments/{paymentReference}`.
2. `PaymentOrchestrationService` looks up the payment in MySQL.
3. If found, it maps the entity to `PaymentResponse`.
4. If not found, it throws `PaymentNotFoundException`, which becomes a `404`.

## 6. Concurrency handling

Concurrency is handled at two levels:

- **Idempotency lock in Redis**: the first request with a key acquires a short-lived lock.
- **Cached response lookup**: duplicate requests poll Redis for a completed response instead of creating a second payment.

This prevents:

- double-charging the same request
- duplicate payment rows
- race conditions between two in-flight requests with the same payload

If the request payload changes while the same idempotency key is reused, the system raises `409 Conflict`.

## 7. Why this is safe for fintech-style workloads

The system is designed around a few fintech-friendly rules:

- Every request has a correlation ID for traceability.
- Payment state is persisted in a database before and after provider execution.
- Provider calls are protected with retries, timeouts, and circuit breakers.
- Idempotency is enforced centrally, not left to the client.
- Errors are normalized into a consistent API schema.

## 8. How it scales to millions of requests

### Stateless API layer

The controller and service layer are stateless, which means more app instances can be added horizontally behind a load balancer.

### Horizontal scaling

- Run multiple app replicas in Docker, Kubernetes, or a cloud container platform.
- Keep the app instances interchangeable and short-lived.
- Use a shared MySQL and Redis cluster so all replicas see the same payment and idempotency state.

### Fast duplicate checks

Redis is used for the hot idempotency path, which keeps duplicate-request checks fast even under load.

### Database durability

MySQL keeps the source of truth for payment records, so the system can recover after restarts.

### Provider isolation

Provider timeouts, retries, and circuit breakers prevent a slow or failing provider from collapsing the whole service.

### Observability

Metrics for latency, success, failures, and retries let you identify:

- slow endpoints
- failing providers
- retry storms
- abnormal duplicate-request patterns

### Practical production scaling notes

For very high traffic, you would usually also add:

- connection pooling tuning
- read replicas for read-heavy lookups
- partitioning or archival for older payment rows
- asynchronous event publishing for downstream consumers
- rate limiting at the gateway
- tracing with OpenTelemetry

## 9. What to say in interviews

Use these talking points:

- The project is a mini fintech payment orchestration service.
- The main challenge is safely handling duplicates and provider failures.
- Redis is used for low-latency idempotency control.
- MySQL stores durable payment state.
- Retry and circuit breaker policies protect the provider integration layer.
- The design is stateless at the API layer, so it can scale horizontally.
- Metrics and correlation IDs make the system operable in production.

## 10. Strong interview answers

### How do you handle duplicate requests?

I use an idempotency key with Redis locks and cached responses. The first request wins, and later requests either reuse the stored response or fail with a conflict if the payload differs.

### How do you handle millions of requests?

I keep the API stateless, scale it horizontally, use Redis for fast idempotency checks, keep MySQL as the durable source of truth, and protect provider calls with retries and circuit breakers.

### How do you handle a slow provider?

The provider call runs through a timeout-aware executor with retry and circuit breaker protection. If the primary provider fails, the workflow falls back to the next provider in the route.

### What makes the system interview-worthy?

It demonstrates backend fundamentals that matter in fintech: data consistency, duplicate protection, failure handling, operational visibility, and scalable service boundaries.
