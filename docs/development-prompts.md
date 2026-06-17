# Development Prompts

This file documents the prompts used while shaping the implementation in a product-focused style.

## Prompt examples

1. "Design a payment orchestration service in Java with create and fetch APIs, idempotency, routing, retries, and provider failover."
2. "Keep the architecture production-style: separate controller, orchestration, routing, provider connector, persistence, and metrics layers."
3. "Add observability with correlation IDs, OpenAPI, and Prometheus metrics without overcomplicating the code."
4. "Prefer minimal, maintainable Spring Boot components and make payment state transitions explicit."
5. "Document the delivery with clear installation, execution, test scenarios, and performance notes."

## Prompting approach

- Start from the product requirements and translate them into concrete layers.
- Keep responsibilities separated so each class stays easy to test.
- Favor explicit state transitions over hidden behavior.
- Preserve idempotency and failure handling as first-class concerns.

