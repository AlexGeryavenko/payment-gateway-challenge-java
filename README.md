# Payment Gateway

Spring Boot REST API that sits between merchants and an acquiring bank. Merchants submit card payments; the gateway validates, forwards to the bank, and stores results.

## Getting Started

### Prerequisites

- JDK 17
- Docker

### Run

```bash
docker compose up -d          # start bank simulator
./gradlew bootRun             # start gateway on :8090
./gradlew test                # run tests
./gradlew build               # compile + test
```

## API Endpoints

| Method | Path            | Success | Error    | Description            |
|--------|-----------------|---------|----------|------------------------|
| POST   | `/payment`      | 200     | 400, 502 | Process a card payment |
| GET    | `/payment/{id}` | 200     | 404      | Retrieve payment by ID |

### Examples

**Process a payment:**

```bash
curl -s -X POST http://localhost:8090/payment \
  -H 'Content-Type: application/json' \
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
curl -s http://localhost:8090/payment/{id}
```

## Configuration

| Property                          | Default                 | Description            |
|-----------------------------------|-------------------------|------------------------|
| `server.port`                     | `8090`                  | Gateway HTTP port      |
| `bank.simulator.url`              | `http://localhost:8080` | Bank simulator base URL |
| `bank.simulator.connect-timeout`  | `10s`                   | Connection timeout     |
| `bank.simulator.read-timeout`     | `10s`                   | Read timeout           |

## Bank Simulator

The bank simulator (Mountebank) runs on port 8080 and decides authorization based on the last digit of the card number:

- **Odd digit** (1, 3, 5, 7, 9) — authorized
- **Even digit** (2, 4, 6, 8) — declined
- **Zero** (0) — 503 error

## API Documentation

Swagger UI: http://localhost:8090/swagger-ui/index.html

## Documentation

- [Implementation Plan](doc/PLAN.md)
- [Payment Gateway OpenAPI Spec](doc/openapi/payment-gateway.yaml)
- [Bank Simulator OpenAPI Spec](doc/openapi/bank-simulator.yaml)
- [C4 Architecture Diagrams](doc/diagram/)
