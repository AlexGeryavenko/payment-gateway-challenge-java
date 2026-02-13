# Payment Gateway

Spring Boot REST API that sits between merchants and an acquiring bank. Merchants submit card payments; the gateway validates, forwards to the bank, and stores results.

## Getting Started

### Prerequisites

- JDK 17
- Docker

### Run

```bash
docker compose up -d          # start all services
./gradlew bootRun             # start gateway on :8090 (local dev)
./gradlew test                # run tests
./gradlew build               # compile + test
./gradlew check               # tests + Checkstyle + SpotBugs + JaCoCo 80%
```

## Services

`docker compose up -d` starts the full stack:

| Service | Image | Port(s) | Description |
|---------|-------|---------|-------------|
| payment-gateway | (local build) | 8090 | Payment Gateway API |
| bank_simulator | bbyars/mountebank:2.8.1 | 8080, 2525 | Mock acquiring bank |
| prometheus | prom/prometheus:v2.48.0 | 9090 | Metrics & alerting rules |
| grafana | grafana/grafana:10.2.2 | 3000 | Dashboards & visualization |
| tempo | grafana/tempo:2.3.1 | 3200, 4317, 4318 | Distributed tracing |
| alertmanager | prom/alertmanager:v0.26.0 | 9093 | Alert notification routing |

## API Endpoints

| Method | Path | Success | Error | Description |
|--------|------|---------|-------|-------------|
| POST | `/v1/payment` | 201 | 400, 429, 502 | Process a card payment |
| GET | `/v1/payment/{id}` | 200 | 404, 429 | Retrieve payment by ID |

### Examples

**Process a payment:**

```bash
curl -s -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2028,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }'
```

**Retrieve a payment:**

```bash
curl -s http://localhost:8090/v1/payment/{id}
```

### Idempotency

POST requests support an optional `Idempotency-Key` header (UUID). When provided:

- First request: processes normally and stores the result
- Subsequent requests with the same key: returns the cached response without calling the bank

This prevents double charges when clients retry timed-out requests. The key is optional — requests without it are processed normally.

```bash
# Safe to retry — same key returns same result
curl -s -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000' \
  -d '{ ... }'
```

### Validation

Card numbers must pass the Luhn checksum in addition to format validation (14-19 digits). Invalid Luhn numbers are rejected with `400 Bad Request` before contacting the bank.

See [Error Reference](doc/ERROR_REFERENCE.md) for the complete list of validation rules and error responses.

### Validation Errors (400)

All validation failures return a `ValidationErrorResponse` with field-level error details:

```json
{
  "status": "Rejected",
  "message": "Validation failed",
  "errors": [
    { "field": "cardNumber", "message": "Card number failed Luhn check" }
  ]
}
```

Error sources:
- **Bean validation** (invalid format, missing fields) — field names from `@Valid` annotations
- **Luhn validation** (invalid card number checksum) — `field: "cardNumber"`
- **Business logic** (expired card) — `field: "expiryDate"`
- **Malformed JSON** — `field: "requestBody"` or the specific field that failed parsing

### Error Responses

Non-validation errors (404, 429, 502, 500) include `correlationId` and `timestamp` for traceability:

```json
{
  "message": "Bank service unavailable",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-02-13T12:00:00Z"
}
```

The `correlationId` matches the `X-Correlation-Id` response header and can be used to trace requests through gateway logs.

## Configuration

All settings are externalized via `.env` (loaded by spring-dotenv):

| Category | Variable | Default | Description |
|----------|----------|---------|-------------|
| Server | `SERVER_PORT` | `8090` | Gateway HTTP port |
| Bank Simulator | `BANK_SIMULATOR_URL` | `http://localhost:8080` | Bank simulator base URL |
| Bank Simulator | `BANK_SIMULATOR_CONNECT_TIMEOUT` | `10s` | Connection timeout |
| Bank Simulator | `BANK_SIMULATOR_READ_TIMEOUT` | `10s` | Read timeout |
| Springdoc | `SPRINGDOC_SWAGGER_ENABLED` | `true` | Enable Swagger UI |
| Springdoc | `SPRINGDOC_API_DOCS_ENABLED` | `true` | Enable OpenAPI docs endpoint |
| Actuator | `MANAGEMENT_ENDPOINTS_INCLUDE` | `health,info,prometheus,metrics` | Exposed actuator endpoints |
| Actuator | `PROMETHEUS_ENDPOINT_ENABLED` | `true` | Enable Prometheus actuator endpoint |
| Actuator | `PROMETHEUS_EXPORT_ENABLED` | `true` | Enable Prometheus metrics export |
| Tracing | `TRACING_SAMPLING_PROBABILITY` | `1.0` | Trace sampling rate (0.0 – 1.0) |
| Tracing | `OTLP_TRACING_ENDPOINT` | `http://localhost:4318/v1/traces` | OTLP HTTP endpoint for traces |
| Metrics | `METRICS_APP_NAME` | `payment-gateway` | Application tag for metrics |
| Rate Limiting | `RATE_LIMIT_POST_CAPACITY` | `200` | POST burst capacity per IP |
| Rate Limiting | `RATE_LIMIT_POST_REFILL_RATE` | `100` | POST tokens/sec refill per IP |
| Rate Limiting | `RATE_LIMIT_GET_CAPACITY` | `1000` | GET burst capacity per IP |
| Rate Limiting | `RATE_LIMIT_GET_REFILL_RATE` | `500` | GET tokens/sec refill per IP |
| Circuit Breaker | `CB_FAILURE_RATE_THRESHOLD` | `50` | Failure % to open circuit |
| Circuit Breaker | `CB_SLOW_CALL_DURATION_THRESHOLD` | `3s` | Slow call threshold |
| Circuit Breaker | `CB_SLOW_CALL_RATE_THRESHOLD` | `80` | Slow call % to open circuit |
| Circuit Breaker | `CB_WAIT_DURATION_IN_OPEN_STATE` | `30s` | Wait before half-open |
| Circuit Breaker | `CB_PERMITTED_CALLS_HALF_OPEN` | `3` | Calls allowed in half-open |
| Circuit Breaker | `CB_SLIDING_WINDOW_SIZE` | `10` | Sliding window size |
| Circuit Breaker | `CB_REGISTER_HEALTH_INDICATOR` | `true` | Expose circuit breaker health |
| Retry | `RETRY_MAX_ATTEMPTS` | `3` | Max retry attempts for bank calls |
| Retry | `RETRY_WAIT_DURATION` | `500ms` | Initial wait between retries |
| Retry | `RETRY_BACKOFF_MULTIPLIER` | `2` | Exponential backoff multiplier |

## Bank Simulator

The bank simulator (Mountebank) runs on port 8080 and decides authorization based on the last digit of the card number:

- **Odd digit** (1, 3, 5, 7, 9) — authorized
- **Even digit** (2, 4, 6, 8) — declined
- **Zero** (0) — 503 error

## Resilience

**Retry with exponential backoff** — Transient bank failures are retried up to 3 times with exponential backoff (500ms base, 2x multiplier). Only `BankCommunicationException` triggers retries. If all attempts fail, the request falls through to the circuit breaker or returns `502 Bad Gateway`.

**Circuit breaker** — Resilience4j wraps the bank client (`bankClient` instance). When the failure rate exceeds the configured threshold, the circuit opens and subsequent requests immediately return `502 Bad Gateway` with `{"message": "Bank service unavailable"}`. After the wait duration, the circuit transitions to half-open and allows a limited number of probe calls.

**Rate limiting** — Bucket4j token-bucket filter applied per IP address. Separate buckets for GET and POST. When exhausted, returns `429 Too Many Requests` with a `Retry-After` header.

## Quality Gates

`./gradlew check` runs all of the following:

| Tool | Purpose | Config | Report |
|------|---------|--------|--------|
| **JaCoCo** | Line coverage >= 80% | `build.gradle` | `build/reports/jacoco/` |
| **Checkstyle** | Google Java Style (tweaked) | `config/checkstyle/checkstyle.xml` | `build/reports/checkstyle/` |
| **SpotBugs + FindSecBugs** | Bug & security analysis | `config/spotbugs/exclude.xml` | `build/reports/spotbugs/` |

### OWASP Dependency-Check

```bash
./gradlew dependencyCheckAnalyze
```

> **Note:** The OWASP Dependency-Check plugin (v8.2.1) uses the deprecated NVD 1.1 data feeds which are no longer available. Upgrading to OWASP DC 9+ (which uses NVD API v2.0) requires **Gradle 8.5+** due to multi-release JAR incompatibilities with Gradle 8.2.1's ASM library. Upgrade the Gradle wrapper before using this task.

### SonarQube

```bash
./gradlew sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<token>
```

## Monitoring & Dashboards

Grafana is available at http://localhost:3000 (anonymous access, no login required).

Pre-provisioned dashboards:

| Dashboard | Description |
|-----------|-------------|
| Application | HTTP request rates, latencies, error rates |
| Business | Payment processing outcomes (authorized, declined, rejected) |
| Infrastructure | JVM memory, GC, CPU, thread pools |
| Alerts | Active and historical alert status |

Tracing is powered by Grafana Tempo. Traces are exported via OTLP (HTTP) and visible in the Tempo data source within Grafana.

## Alerting

Prometheus evaluates alert rules defined in `monitoring/prometheus/alert-rules.yml`. Alerts route through Alertmanager on port 9093.

| Alert | Condition | Duration | Severity |
|-------|-----------|----------|----------|
| HighBankErrorRate | Bank 502 errors > 5% of calls | 2m | critical |
| HighRejectionRate | REJECTED > 30% of payments | 5m | warning |
| HighLatency | p99 latency > 5s | 3m | warning |
| HighErrorRate | 5xx errors > 1% of requests | 2m | critical |
| HighHeapUsage | JVM heap > 85% | 5m | warning |

## API Documentation

Swagger UI: http://localhost:8090/swagger-ui/index.html

## Documentation

- [Implementation Plan](doc/PLAN.md)
- [Error Reference](doc/ERROR_REFERENCE.md)
- [Payment Gateway OpenAPI Spec](doc/openapi/payment-gateway.yaml)
- [Bank Simulator OpenAPI Spec](doc/openapi/bank-simulator.yaml)
- [C4 Architecture Diagrams](doc/diagram/)
