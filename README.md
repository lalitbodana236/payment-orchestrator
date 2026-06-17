# Payment Orchestrator

Portfolio-ready payment orchestration project that demonstrates routing, resilience, idempotency, observability, and scalable backend design in Spring Boot.

## Overview

This service:

- Creates payments through a routing layer
- Fetches payment details by payment reference
- Routes `CARD` traffic to `PROVIDER_A` and `UPI` traffic to `PROVIDER_B`
- Retries provider execution with exponential backoff
- Falls back to the secondary provider when the primary path fails
- Deduplicates repeated create requests with idempotency keys
- Tracks payment lifecycle states in persistence
- Exposes observability through Prometheus metrics and OpenAPI

## Architecture

`Client -> Controller -> Orchestration Service -> Routing Strategy -> Provider Executor -> Provider Connector -> MySQL / Redis`

### Main Components

- `PaymentController`: REST entry point for create/fetch flows
- `PaymentOrchestrationService`: coordinates persistence, routing, retries, and failover
- `ProviderExecutor`: applies retry policy, timeout handling, and circuit breaker protection
- `IdempotencyService`: stores and validates idempotent request state in Redis and MySQL
- `PaymentMetricsRecorder`: records latency, success/failure, and retry metrics

## Functional Coverage

- `Create Payment API`: `POST /api/v1/payments`
- `Fetch Payment API`: `GET /api/v1/payments/{paymentReference}`
- `Routing`: `CARD -> PROVIDER_A`, `UPI -> PROVIDER_B`
- `Retry & Failover`: provider retries use Spring Retry and secondary provider fallback is handled in orchestration
- `Idempotency`: same key + same payload returns the cached response; same key + different payload raises conflict
- `Payment Status Tracking`: lifecycle is persisted in `payments` with `CREATED`, `PROCESSING`, `RETRYING`, `SUCCESS`, and `FAILED`

## Non-Functional Requirements

- Request payload validation
- Correlation ID propagation for traceability
- API latency and retry counters via Micrometer
- OpenAPI docs for API discovery
- Graceful shutdown and externalized configuration

## Installation

### Prerequisites

- Java 21
- Maven 3.9+
- MySQL 8+
- Redis 7+
- Docker, if you want to run the Testcontainers-based tests

### Configuration

Environment variables are optional because defaults are provided in `src/main/resources/application.properties`.

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`

### Run Locally

```bash
mvn spring-boot:run
```

The service starts on `http://localhost:10005`.

### Run with Docker

Build and start the full stack:

```bash
docker compose up --build
```

This starts:

- `app` on `http://localhost:10005`
- `mysql` on `localhost:3307`
- `redis` on `localhost:6379`

Stop everything with:

```bash
docker compose down
```

## API Usage

### Create a Payment

`POST /api/v1/payments`

Headers:

- `Idempotency-Key: unique-key`
- `X-Correlation-Id: optional-correlation-id`

Request body:

```json
{
  "amount": 120.50,
  "currency": "USD",
  "paymentMethod": "CARD"
}
```

### Fetch a Payment

`GET /api/v1/payments/{paymentReference}`

Example:

```bash
curl http://localhost:10005/api/v1/payments/pay_123
```

### API Docs

- Swagger UI: `http://localhost:10005/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:10005/v3/api-docs`

## Persistence Model

- `payments`: stores payment reference, amount, currency, method, provider, status, retry count, failure reason, and timestamps
- `idempotency_keys`: stores request hash and serialized response for duplicate request protection

## Integration Points

- MySQL for payment and idempotency persistence
- Redis for idempotency locks and cached responses
- Micrometer / Prometheus for metrics export
- Resilience4j for circuit breaker support

## Request and Response Fields

### Create Payment Request

- `amount`: decimal value greater than `0`
- `currency`: 3-letter ISO-like uppercase currency code
- `paymentMethod`: `CARD` or `UPI`
- `Idempotency-Key`: required header for deduplication

### Create Payment Response

- `paymentReference`
- `amount`
- `currency`
- `paymentMethod`
- `provider`
- `status`
- `retryCount`
- `failureReason`
- `createdAt`
- `updatedAt`

### Fetch Payment Response

Same response schema as create, wrapped in the API response envelope.

## Test Strategy

See `docs/test-cases.md` for the full scenario list and coverage breakdown.
The repository also includes sample unit tests for routing, mapping, hashing, and payment route selection.

## Prompt Log

See `docs/development-prompts.md` for the development prompt record and design intent.

## Performance Notes

See `docs/performance-notes.md` for the metrics and tuning considerations.

## Project Document

See `docs/project-document.md` for the class-by-class explanation, request flow, concurrency handling, and scaling discussion you can use in interviews.

