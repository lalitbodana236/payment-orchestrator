# Payment Orchestrator

Portfolio-ready payment orchestration project that demonstrates routing, resilience, idempotency, observability, and scalable backend design in Spring Boot.

## Overview

This service:

- Creates payments through a routing layer
- Fetches payment details by payment reference
- Routes `DEBIT_CARD`, `CREDIT_CARD`, and `NET_BANKING` traffic to `PROVIDER_A`
- Routes `UPI` traffic to `PROVIDER_B`
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
- `Routing`: `DEBIT_CARD/CREDIT_CARD/NET_BANKING -> PROVIDER_A`, `UPI -> PROVIDER_B`
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
- `prometheus` on `localhost:9090`
- `grafana` on `localhost:3000` with `admin` / `admin`

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
  "paymentMethod": "DEBIT_CARD"
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

## Observability

You can inspect the custom metrics recorded by `PaymentMetricsRecorder` here:

- Application metrics snapshot: `GET /api/v1/observability/metrics`
- Actuator metrics: `GET /actuator/metrics`
- Prometheus export: `GET /actuator/prometheus`
- Prometheus UI: `http://localhost:9090`
- Grafana UI: `http://localhost:3000`

The application records metrics such as:

- `payment.api.latency`
- `payment.processed.total`
- `payment.retry.total`

These show request timing, payment outcomes, and retry activity by provider and failure reason.

Grafana is pre-provisioned with the Prometheus datasource and a starter dashboard for payment throughput, retries, and average latency.

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
- `paymentMethod`: `DEBIT_CARD`, `CREDIT_CARD`, `UPI`, or `NET_BANKING`
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

## Interview Pitch

If someone asks, “Tell me about your project,” you can say:

> I built a Spring Boot payment orchestration service that routes debit card, credit card, UPI, and net banking payments to the right provider, protects the system with idempotency, retries, and circuit breakers, and stores payment state in MySQL with Redis for fast duplicate detection.  
> The goal was to solve a real fintech problem: payment APIs must stay correct under retries, provider failures, and concurrent traffic.  
> My solution keeps the API stateless so it can scale horizontally, normalizes errors, records metrics for observability, and ensures the same payment request never gets processed twice.  
> The impact is a backend that behaves like a production payment platform and demonstrates the engineering tradeoffs you need in fintech systems.

## Why This Project Matters

- It solves a real payment reliability problem instead of a basic CRUD use case.
- It shows practical backend engineering: consistency, failure handling, concurrency, and scaling.
- It demonstrates production-minded thinking with metrics, OpenAPI, Redis, and durable storage.
- It gives you a strong interview story for fintech and payments roles.

## Resume Description

Built a Spring Boot payment orchestration backend for fintech-style transaction processing, with provider routing, Redis-backed idempotency, MySQL persistence, retries, failover, and observability. Designed the service to handle duplicate requests safely, isolate provider failures, and scale horizontally under high traffic. The project demonstrates production-ready backend thinking for transactional systems.

## GitHub Showcase

**Project:** Payment Orchestrator  
**Repository:** `https://github.com/lalitbodana236/payment-orchestrator`
**GitHub Pages:** `https://lalitbodana236.github.io/payment-orchestrator/`

### What it demonstrates

- Payment routing for `DEBIT_CARD`, `CREDIT_CARD`, `UPI`, and `NET_BANKING`
- Idempotent request handling with Redis and MySQL
- Retry and failover for provider resilience
- Metrics, OpenAPI docs, and correlation IDs for observability
- Docker-based local deployment

### Quick pitch

This project is a fintech backend that focuses on correctness under retries, duplicate submissions, and provider failures. It shows how to design a stateless service that can scale horizontally while keeping payment processing safe and traceable.

### Pages setup

- Source folder: `docs/`
- Home page: `docs/index.html`
- Project document: `docs/project-document.html`
- Markdown source: `docs/project-document.md`

Enable GitHub Pages from the repository settings and publish the `docs/` folder.
