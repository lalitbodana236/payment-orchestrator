# Test Cases and Coverage Plan

This document is a practical test matrix for the payment orchestrator. It covers happy paths, validation, idempotency, routing, provider failure handling, observability, and error responses.

## 1. Test strategy

- **Unit tests**: cover pure logic such as routing, mapping, hashing, entity lifecycle hooks, and exception translation.
- **Controller tests**: verify request validation, headers, and API response shapes.
- **Service tests**: verify idempotency, orchestration, provider fallback, and retries.
- **Observability tests**: verify custom metrics are recorded and visible.
- **Integration tests**: verify database and Redis interactions in a real containerized environment.

## 2. Functional test cases

| ID | Scenario | Input | Expected result |
|---|---|---|---|
| F1 | Create payment with `DEBIT_CARD` | Valid body + unique idempotency key | `201 Created`, payment persisted, primary provider selected |
| F2 | Create payment with `CREDIT_CARD` | Valid body + unique idempotency key | `201 Created`, payment persisted, primary provider selected |
| F3 | Create payment with `UPI` | Valid body + unique idempotency key | `201 Created`, payment persisted, UPI provider selected |
| F4 | Create payment with `NET_BANKING` | Valid body + unique idempotency key | `201 Created`, payment persisted, primary provider selected |
| F5 | Fetch existing payment | Known payment reference | `200 OK` with payment details |
| F6 | Repeat same create request | Same idempotency key and same payload | Cached response returned, no duplicate payment created |
| F7 | Reuse key with different payload | Same idempotency key, different body | `409 Conflict` |
| F8 | Missing idempotency key | Omit `Idempotency-Key` header | `400 Bad Request` |

## 3. Validation test cases

| ID | Scenario | Expected result |
|---|---|---|
| V1 | `amount <= 0` | `400 Bad Request` |
| V2 | `amount` missing | `400 Bad Request` |
| V3 | `currency` lowercase | `400 Bad Request` |
| V4 | `currency` not 3 letters | `400 Bad Request` |
| V5 | `paymentMethod` missing | `400 Bad Request` |
| V6 | Malformed JSON body | `400 Bad Request` |

## 4. Routing and failover test cases

| ID | Scenario | Expected result |
|---|---|---|
| R1 | `DEBIT_CARD` request | Route chooses `PROVIDER_A` first |
| R2 | `CREDIT_CARD` request | Route chooses `PROVIDER_A` first |
| R3 | `NET_BANKING` request | Route chooses `PROVIDER_A` first |
| R4 | `UPI` request | Route chooses `PROVIDER_B` first |
| R5 | Primary provider succeeds | Payment completes on first provider |
| R6 | Primary provider fails with retryable error | Retry occurs, then fallback provider is attempted |
| R7 | Primary provider timeout | Timeout is surfaced and fallback can be attempted |
| R8 | All providers fail | Payment marked `FAILED` |

## 5. Idempotency test cases

| ID | Scenario | Expected result |
|---|---|---|
| I1 | First request with new key | Lock is created and request executes |
| I2 | Same key + same payload | Cached response returned |
| I3 | Same key + different payload | `409 Conflict` |
| I4 | Duplicate request while first is in progress | Second request waits for completion or times out with conflict |
| I5 | Redis response cache hit | Database work is skipped |
| I6 | Redis lock release after success | Lock is removed only by owning token |

## 6. Observability test cases

| ID | Scenario | Expected result |
|---|---|---|
| O1 | Successful create payment | `payment.api.latency{operation=create_payment,outcome=success}` recorded |
| O2 | Failed create payment | `payment.api.latency{operation=create_payment,outcome=failure}` recorded |
| O3 | Successful fetch payment | `payment.api.latency{operation=get_payment,outcome=success}` recorded |
| O4 | Retryable provider failure | `payment.retry.total` increments |
| O5 | Successful payment processing | `payment.processed.total{outcome=success}` increments |
| O6 | Failed payment processing | `payment.processed.total{outcome=failure}` increments |

## 7. Error handling test cases

| ID | Scenario | Expected result |
|---|---|---|
| E1 | Payment not found | `404 Not Found` |
| E2 | Validation failure | `400 Bad Request` with field details |
| E3 | Missing header | `400 Bad Request` |
| E4 | Idempotency conflict | `409 Conflict` |
| E5 | Concurrent duplicate request | `409 Conflict` |
| E6 | Provider timeout | `502 Bad Gateway` or mapped processing error |
| E7 | Unexpected runtime exception | `500 Internal Server Error` |

## 8. Integration test cases

| ID | Scenario | Expected result |
|---|---|---|
| INT1 | MySQL persistence works | Payment stored and retrieved correctly |
| INT2 | Redis idempotency lock works | Duplicate request protection behaves correctly |
| INT3 | Full create-payment flow | Request → provider → persistence → response |
| INT4 | Full fetch-payment flow | Request → repository → response |

## 9. Code coverage plan

The goal is to cover the critical logic that makes the project interview-worthy:

- **Routing logic**: `PaymentRoutingStrategy`, `PaymentRoute`
- **Mapping logic**: `PaymentMapper`
- **Utility logic**: `HashingUtils`
- **API layer**: `PaymentController`, `ObservabilityController`
- **Error handling**: `GlobalExceptionHandler`
- **Domain lifecycle**: `PaymentEntity`, `IdempotencyKeyEntity`
- **Idempotency**: `IdempotencyService`
- **Metrics exposure**: `PaymentMetricsRecorder` and observability endpoint

## 10. Suggested automation focus

- Controller tests for request validation and API status codes.
- Service tests for routing, idempotency, and failover behavior.
- Repository tests for persistence mapping.
- Integration tests with Testcontainers for MySQL and Redis.

## 11. Current coverage snapshot

Generated with JaCoCo after the test suite passed.

- Line coverage: `53.4%`
- Branch coverage: `50.0%`
- Method coverage: `67.5%`

Coverage report:

- `target/site/jacoco/index.html`
- `target/site/jacoco/jacoco.csv`
