# Performance Notes

## Captured metrics

- `payment.api.latency`
  - Tags: `operation`, `outcome`
  - Measures time spent in create and fetch API flows.
- `payment.processed.total`
  - Tags: `outcome`, `provider`
  - Counts successful and failed payment outcomes.
- `payment.retry.total`
  - Tags: `provider`, `reason`
  - Tracks retry activity and failure reasons.

## Configuration that affects performance

- Provider timeout is controlled by `payment.orchestration.provider.timeout-ms`.
- Retry behavior is controlled by `payment.orchestration.retry.*`.
- Idempotency wait and polling are controlled by `payment.orchestration.idempotency.*`.
- Circuit breaker thresholds are configured per provider in `application.properties`.

## Notes

- The service already exposes Prometheus-compatible metrics through Spring Boot Actuator.
- The metrics are useful for latency tracking, retry analysis, and provider health monitoring.
- For a stronger portfolio project, you could add benchmark results from a representative load test and include the observed p95 latency, retry rate, and error rate.

