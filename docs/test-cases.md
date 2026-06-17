# Test Cases

This document classifies the project scenarios into sanity, regression, and integration coverage. It includes both positive and negative cases.

## Sanity

| ID | Scenario | Input | Expected result |
|---|---|---|---|
| S1 | Create payment with valid `CARD` request | Valid body + unique idempotency key | `201 Created`, payment persisted, provider `PROVIDER_A` selected |
| S2 | Create payment with valid `UPI` request | Valid body + unique idempotency key | `201 Created`, payment persisted, provider `PROVIDER_B` selected |
| S3 | Fetch existing payment | Known `paymentReference` | `200 OK` with payment details |
| S4 | Repeat same create request | Same idempotency key and same payload | Cached response returned, no duplicate payment created |

## Regression

| ID | Scenario | Input | Expected result |
|---|---|---|---|
| R1 | Invalid amount | `amount <= 0` | `400 Bad Request` |
| R2 | Invalid currency | Lowercase or non-3-letter currency | `400 Bad Request` |
| R3 | Missing payment method | Omitted `paymentMethod` | `400 Bad Request` |
| R4 | Missing idempotency header | No `Idempotency-Key` | `400 Bad Request` or framework-level validation failure |
| R5 | Fetch unknown payment | Invalid `paymentReference` | `404 Not Found` |
| R6 | Idempotency conflict | Same key, different payload | `409 Conflict` |

## Integration

| ID | Scenario | Input | Expected result |
|---|---|---|---|
| I1 | Primary provider succeeds | `CARD` request | Provider A response stored, status `SUCCESS` |
| I2 | Primary provider fails and failover succeeds | Primary provider returns retryable failure | Secondary provider used, status `SUCCESS` |
| I3 | Provider timeout | Connector exceeds timeout | Retry occurs, then failover or failure response |
| I4 | Retryable provider exception | `TransientProviderException` | Retry template performs configured attempts |
| I5 | Circuit breaker open | Provider repeatedly failing | Provider execution rejected and surfaced as payment processing failure |
| I6 | Concurrent duplicate create request | Two requests with same idempotency key in flight | One request executes, other waits or receives conflict/in-progress response |
| I7 | Redis unavailable | Idempotency lock/cache unavailable | Request fails fast or surfaces infrastructure error for diagnosis |
| I8 | MySQL unavailable | Persistence layer failure | Request fails with server error, no false success returned |

## Negative scenarios

| ID | Scenario | Expected result |
|---|---|---|
| N1 | Malformed JSON body | `400 Bad Request` |
| N2 | Missing amount field | `400 Bad Request` |
| N3 | Unsupported payment method | Validation failure |
| N4 | Idempotent replay with modified payload | `409 Conflict` |
| N5 | Provider returns permanent failure | Payment eventually marked `FAILED` |
| N6 | Unexpected runtime exception | `500` or mapped processing error response |

## Suggested automation focus

- Controller tests for request validation and API status codes.
- Service tests for routing, idempotency, and failover behavior.
- Repository tests for persistence mapping.
- Integration tests with Testcontainers for MySQL and Redis.

