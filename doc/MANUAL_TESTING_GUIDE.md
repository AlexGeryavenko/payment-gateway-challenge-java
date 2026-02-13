# Manual Testing Guide

This guide walks through starting the Payment Gateway locally and manually testing every functional and non-functional requirement using `curl`.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Start the Environment](#2-start-the-environment)
3. [Verify Services Are Running](#3-verify-services-are-running)
4. [Functional Requirements](#4-functional-requirements)
   - 4.1 [Process Payment — Authorized](#41-process-payment--authorized)
   - 4.2 [Process Payment — Declined](#42-process-payment--declined)
   - 4.3 [Process Payment — Expired Card (Rejected)](#43-process-payment--expired-card-rejected)
   - 4.4 [Retrieve Payment by ID](#44-retrieve-payment-by-id)
   - 4.5 [Retrieve Non-Existent Payment](#45-retrieve-non-existent-payment)
   - 4.6 [PCI Compliance — Card Number Masked](#46-pci-compliance--card-number-masked)
   - 4.7 [PCI Compliance — CVV Never Returned](#47-pci-compliance--cvv-never-returned)
   - 4.8 [Rejected Payments Are Not Stored](#48-rejected-payments-are-not-stored)
5. [Input Validation (Rejected — 400)](#5-input-validation-rejected--400)
   - 5.1 [Card Number Too Short](#51-card-number-too-short)
   - 5.2 [Card Number Too Long](#52-card-number-too-long)
   - 5.3 [Card Number With Letters](#53-card-number-with-letters)
   - 5.4 [CVV Too Short](#54-cvv-too-short)
   - 5.5 [CVV Too Long](#55-cvv-too-long)
   - 5.6 [CVV With Letters](#56-cvv-with-letters)
   - 5.7 [Amount Zero](#57-amount-zero)
   - 5.8 [Amount Negative](#58-amount-negative)
   - 5.9 [Expiry Month Zero](#59-expiry-month-zero)
   - 5.10 [Expiry Month > 12](#510-expiry-month--12)
   - 5.11 [Invalid Currency](#511-invalid-currency)
   - 5.12 [Missing Required Fields](#512-missing-required-fields)
   - 5.13 [Empty JSON Body](#513-empty-json-body)
   - 5.14 [Malformed JSON](#514-malformed-json)
   - 5.15 [Luhn-Invalid Card Number](#515-luhn-invalid-card-number)
6. [Non-Functional Requirements](#6-non-functional-requirements)
   - 6.0 [Idempotency](#60-idempotency)
   - 6.0.1 [Retry with Exponential Backoff](#601-retry-with-exponential-backoff)
   - 6.0.2 [Correlation ID in Error Bodies](#602-correlation-id-in-error-bodies)
   - 6.1 [Rate Limiting](#61-rate-limiting)
   - 6.2 [Circuit Breaker](#62-circuit-breaker)
   - 6.3 [Correlation ID](#63-correlation-id)
   - 6.4 [Metrics (Prometheus)](#64-metrics-prometheus)
   - 6.5 [Health Check](#65-health-check)
   - 6.6 [Swagger / OpenAPI](#66-swagger--openapi)
   - 6.7 [Grafana Dashboards](#67-grafana-dashboards)
   - 6.8 [Distributed Tracing (Tempo)](#68-distributed-tracing-tempo)
7. [Cleanup](#7-cleanup)

---

## 1. Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| JDK | 17 | `sdk install java 17.0.12-oracle` (SDKMAN) |
| Docker + Compose | Latest | [docker.com](https://www.docker.com/) |
| curl | Any | Pre-installed on macOS/Linux |
| jq *(optional)* | Any | `brew install jq` — for readable JSON output |

## 2. Start the Environment

Choose **one** of the two options below:

### Option A: Everything in Docker (recommended)

Starts the app and all infrastructure (bank simulator, Prometheus, Grafana, Tempo, Alertmanager) as containers.

```bash
docker compose up -d
```

The app container (`payment-gateway`) runs on **port 8090**. Wait ~10 seconds for it to build and start.

### Option B: App locally + infrastructure in Docker

Useful when you're iterating on code and want fast restarts without rebuilding the Docker image.

```bash
# 1. Start only the infrastructure (excludes the app container)
docker compose up -d bank_simulator prometheus grafana tempo alertmanager

# 2. Start the app locally
./gradlew bootRun
```

Leave `bootRun` running in this terminal and open a second one for `curl` commands.

## 3. Verify Services Are Running

```bash
# Payment Gateway health
curl -s http://localhost:8090/actuator/health | jq .
# Expected: {"status": "UP", ...}

# Bank simulator (Mountebank admin)
curl -s http://localhost:2525/imposters | jq .
# Expected: JSON with port 8080 imposter

# Prometheus
curl -s http://localhost:9090/-/healthy
# Expected: "Prometheus Server is Healthy."

# Grafana
curl -s http://localhost:3000/api/health | jq .
# Expected: {"database": "ok"}
```

---

## 4. Functional Requirements

### 4.1 Process Payment — Authorized

Card numbers ending in an **odd digit** (1, 3, 5, 7, 9) are authorized by the bank.

```bash
curl -s -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 10000,
    "cvv": "123"
  }' | jq .
```

**Expected:** HTTP 201

```json
{
  "id": "<uuid>",
  "status": "Authorized",
  "cardNumberLastFour": "8877",
  "expiryMonth": 4,
  "expiryYear": 2027,
  "currency": "GBP",
  "amount": 10000
}
```

**Save the `id`** for later retrieval tests:
```bash
PAYMENT_ID=$(curl -s -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 10000,
    "cvv": "123"
  }' | jq -r '.id')
echo "Payment ID: $PAYMENT_ID"
```

### 4.2 Process Payment — Declined

Card numbers ending in an **even digit** (2, 4, 6, 8) are declined by the bank.

```bash
curl -s -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248878",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "USD",
    "amount": 5000,
    "cvv": "456"
  }' | jq .
```

**Expected:** HTTP 201

```json
{
  "id": "<uuid>",
  "status": "Declined",
  "cardNumberLastFour": "8878",
  "expiryMonth": 4,
  "expiryYear": 2027,
  "currency": "USD",
  "amount": 5000
}
```

### 4.3 Process Payment — Expired Card (Rejected)

An expired card is rejected **without contacting the bank**.

```bash
curl -s -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 1,
    "expiryYear": 2020,
    "currency": "GBP",
    "amount": 10000,
    "cvv": "123"
  }' | jq .
```

**Expected:** HTTP 400

```json
{
  "status": "Rejected",
  "message": "Validation failed",
  "errors": [
    {
      "field": "expiryDate",
      "message": "Card expiry date must be in the future"
    }
  ]
}
```

### 4.4 Retrieve Payment by ID

Use the `$PAYMENT_ID` saved from [section 4.1](#41-process-payment--authorized).

```bash
curl -s http://localhost:8090/v1/payment/$PAYMENT_ID | jq .
```

**Expected:** HTTP 200 — same fields as the original POST response (status, last four, amount, currency, etc.)

### 4.5 Retrieve Non-Existent Payment

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  http://localhost:8090/v1/payment/00000000-0000-0000-0000-000000000000
```

**Expected:** HTTP 404

```json
{
  "message": "Page not found"
}
```

### 4.6 PCI Compliance — Card Number Masked

Verify that **neither** the POST nor GET response contains the full card number — only `cardNumberLastFour`.

```bash
# POST response should have cardNumberLastFour, never the full number
curl -s -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "EUR",
    "amount": 999,
    "cvv": "1234"
  }' | jq 'has("cardNumber", "cardNumberLastFour")'
# Expected: false true  (no "cardNumber" key, yes "cardNumberLastFour")
```

```bash
# Alternative check: grep for full card number — should NOT appear
curl -s -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "EUR",
    "amount": 999,
    "cvv": "1234"
  }' | grep -c "2222405343248877"
# Expected: 0 (not found)
```

### 4.7 PCI Compliance — CVV Never Returned

```bash
curl -s -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 500,
    "cvv": "789"
  }' | jq 'has("cvv")'
# Expected: false
```

### 4.8 Rejected Payments Are Not Stored

```bash
# POST an expired card (rejected)
REJECTED_RESPONSE=$(curl -s -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 1,
    "expiryYear": 2020,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }')
echo "$REJECTED_RESPONSE" | jq .
# status should be "Rejected", with field-level errors

# Confirm: no id field is present, so it was never stored
echo "$REJECTED_RESPONSE" | jq 'has("id")'
# Expected: false
```

---

## 5. Input Validation (Rejected — 400)

All of these should return **HTTP 400** with a `ValidationErrorResponse`:

```json
{
  "status": "Rejected",
  "message": "Validation failed",
  "errors": [
    { "field": "<fieldName>", "message": "<description>" }
  ]
}
```

### 5.1 Card Number Too Short

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "1234567890123",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }'
```
13 digits — must be 14-19. **Expected:** HTTP 400 with `errors[0].field = "cardNumber"`.

### 5.2 Card Number Too Long

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "12345678901234567890",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }'
```
20 digits — must be 14-19. **Expected:** HTTP 400 with `errors[0].field = "cardNumber"`.

### 5.3 Card Number With Letters

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222ABCD43248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }'
```
**Expected:** HTTP 400 with `errors[0].field = "cardNumber"`.

### 5.4 CVV Too Short

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "12"
  }'
```
2 digits — must be 3-4. **Expected:** HTTP 400 with `errors[0].field = "cvv"`.

### 5.5 CVV Too Long

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "12345"
  }'
```
5 digits — must be 3-4. **Expected:** HTTP 400 with `errors[0].field = "cvv"`.

### 5.6 CVV With Letters

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "12A"
  }'
```
**Expected:** HTTP 400 with `errors[0].field = "cvv"`.

### 5.7 Amount Zero

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 0,
    "cvv": "123"
  }'
```
**Expected:** HTTP 400 with `errors[0].field = "amount"`.

### 5.8 Amount Negative

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": -500,
    "cvv": "123"
  }'
```
**Expected:** HTTP 400 with `errors[0].field = "amount"`.

### 5.9 Expiry Month Zero

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 0,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }'
```
**Expected:** HTTP 400 with `errors[0].field = "expiryMonth"`.

### 5.10 Expiry Month > 12

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 13,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }'
```
**Expected:** HTTP 400 with `errors[0].field = "expiryMonth"`.

### 5.11 Invalid Currency

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "JPY",
    "amount": 100,
    "cvv": "123"
  }'
```
Only `GBP`, `USD`, `EUR` accepted. **Expected:** HTTP 400 with `errors[0].field = "currency"`.

### 5.12 Missing Required Fields

```bash
# Missing cardNumber
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }'

# Missing cvv
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100
  }'

# Missing amount
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "cvv": "123"
  }'
```
**Expected:** HTTP 400 with field-level errors indicating the missing field for each.

### 5.13 Empty JSON Body

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{}'
```
**Expected:** HTTP 400 with multiple field-level errors in the `errors` array.

### 5.14 Malformed JSON

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d 'this is not json'
```
**Expected:** HTTP 400 with `errors[0].field = "requestBody"` and `errors[0].message = "Malformed JSON request body"`.

### 5.15 Luhn-Invalid Card Number

A card number with valid length but failing the Luhn checksum is rejected before contacting the bank.

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "11111111111111",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }'
```

**Expected:** HTTP 400 with `errors[0].field = "cardNumber"` and `errors[0].message = "Card number failed Luhn check"`.

---

## 6. Non-Functional Requirements

### 6.0 Idempotency

POST requests support an optional `Idempotency-Key` header. When provided, duplicate requests return the cached response.

**Test: First request processes normally**

```bash
IDEM_KEY=$(uuidgen)
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }' | jq .
```

**Expected:** HTTP 201 with `status: "Authorized"`.

**Test: Same key returns cached response (no duplicate bank call)**

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }' | jq .
```

**Expected:** HTTP 201 with the **same `id`** as the first request.

**Test: Without idempotency key, processes normally**

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }' | jq .
```

**Expected:** HTTP 201 with a new unique `id`.

### 6.0.1 Retry with Exponential Backoff

Bank calls are retried up to 3 times with exponential backoff before returning 502. To test, use card ending in `0` which triggers a bank 503:

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248870",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }'
```

**Expected:** HTTP 502 after ~3.5 seconds (3 attempts with 500ms + 1000ms + 2000ms backoff). Check application logs for 3 retry attempts.

### 6.0.2 Correlation ID in Error Bodies

Non-validation error responses now include `correlationId` and `timestamp` in the body.

```bash
curl -s http://localhost:8090/v1/payment/00000000-0000-0000-0000-000000000000 | jq .
```

**Expected:** HTTP 404 with:

```json
{
  "message": "Page not found",
  "correlationId": "<uuid>",
  "timestamp": "<iso-8601>"
}
```

The `correlationId` matches the `X-Correlation-Id` response header.

### 6.1 Rate Limiting

The gateway uses per-IP token bucket rate limiting (Bucket4j). Defaults: POST 200 burst / 100 per sec, GET 1000 burst / 500 per sec.

**Test: Exhaust POST rate limit**

Override with low limits to test easily. Stop the app, then restart with:

```bash
RATE_LIMIT_POST_CAPACITY=3 RATE_LIMIT_POST_REFILL_RATE=1 ./gradlew bootRun
```

Then in another terminal, rapidly fire 5 requests:

```bash
for i in 1 2 3 4 5; do
  echo "--- Request $i ---"
  curl -s -w "HTTP Status: %{http_code}\n" \
    -X POST http://localhost:8090/v1/payment \
    -H 'Content-Type: application/json' \
    -d '{
      "cardNumber": "2222405343248877",
      "expiryMonth": 4,
      "expiryYear": 2027,
      "currency": "GBP",
      "amount": 100,
      "cvv": "123"
    }' -o /dev/null
done
```

**Expected:** First 3 return HTTP 201; requests 4-5 return HTTP 429 with:

```json
{
  "message": "Rate limit exceeded. Try again later."
}
```

**Check rate-limit headers:**

```bash
curl -s -D - -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }' -o /dev/null 2>&1 | grep -iE "x-rate-limit|retry-after"
```

**Expected:** `X-Rate-Limit-Remaining` on success, `Retry-After` on 429.

**Test: GET and POST have separate buckets**

After exhausting POST capacity, a GET request should still succeed:

```bash
curl -s http://localhost:8090/v1/payment/$PAYMENT_ID | jq .
# Expected: HTTP 200 (GET bucket is separate)
```

**Test: Actuator endpoints are not rate-limited**

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  http://localhost:8090/actuator/health
# Expected: HTTP 200 even if rate limit is exhausted
```

> Remember to restart the app with default settings after testing.

### 6.2 Circuit Breaker

The `bankClient` circuit breaker (Resilience4j) opens after 50% failure rate over a sliding window of 10 calls.

**Test: Trigger circuit breaker with card ending in 0 (bank returns 503)**

Override with small window for easier testing:

```bash
CB_SLIDING_WINDOW_SIZE=4 CB_FAILURE_RATE_THRESHOLD=50 CB_WAIT_DURATION_IN_OPEN_STATE=60s ./gradlew bootRun
```

Send 4 requests with a card ending in `0` (bank error):

```bash
for i in 1 2 3 4; do
  echo "--- Request $i ---"
  curl -s -w "HTTP Status: %{http_code}\n" \
    -X POST http://localhost:8090/v1/payment \
    -H 'Content-Type: application/json' \
    -d '{
      "cardNumber": "2222405343248870",
      "expiryMonth": 4,
      "expiryYear": 2027,
      "currency": "GBP",
      "amount": 100,
      "cvv": "123"
    }'
  echo
done
```

**Expected:** Requests 1-4 return HTTP 502 (bank error). Now the circuit should be open.

Send one more request (even with a valid card):

```bash
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }'
```

**Expected:** HTTP 502 with:

```json
{
  "message": "Bank service unavailable"
}
```

The circuit breaker is now open and rejecting calls without contacting the bank. It will transition to half-open after 60 seconds.

### 6.3 Correlation ID

Every request/response should carry an `X-Correlation-Id` header.

**Test: Header generated when not provided**

```bash
curl -s -D - -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }' -o /dev/null 2>&1 | grep -i "x-correlation-id"
```

**Expected:** `X-Correlation-Id: <some-uuid>` present in response headers.

**Test: Header echoed when provided**

```bash
curl -s -D - -X POST http://localhost:8090/v1/payment \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: my-custom-correlation-123' \
  -d '{
    "cardNumber": "2222405343248877",
    "expiryMonth": 4,
    "expiryYear": 2027,
    "currency": "GBP",
    "amount": 100,
    "cvv": "123"
  }' -o /dev/null 2>&1 | grep -i "x-correlation-id"
```

**Expected:** `X-Correlation-Id: my-custom-correlation-123` echoed back.

### 6.4 Metrics (Prometheus)

```bash
# All payment-related metrics
curl -s http://localhost:8090/actuator/prometheus | grep "payment\."

# Specific counters to look for after making some requests:
curl -s http://localhost:8090/actuator/prometheus | grep "payment_processed_total"
# Expected: payment_processed_total{currency="GBP",status="AUTHORIZED",...} 1.0

curl -s http://localhost:8090/actuator/prometheus | grep "payment_amount"
# Expected: distribution summary with count, sum, max

curl -s http://localhost:8090/actuator/prometheus | grep "bank_authorization_duration"
# Expected: timer with count, sum, max

curl -s http://localhost:8090/actuator/prometheus | grep "payment_retrieved_total"
# Expected: payment_retrieved_total{found="true",...} 1.0
```

**Verify Prometheus is scraping the gateway:**

Open http://localhost:9090/targets — the `payment-gateway` target should show "UP".

### 6.5 Health Check

```bash
curl -s http://localhost:8090/actuator/health | jq .
```

**Expected:**

```json
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "bankClient": { "status": "UP", ... }
      }
    },
    ...
  }
}
```

After circuit breaker opens, health should reflect it:

```bash
# After triggering circuit breaker (section 6.2)
curl -s http://localhost:8090/actuator/health | jq '.components.circuitBreakers'
```

### 6.6 Swagger / OpenAPI

Open in browser: http://localhost:8090/swagger-ui/index.html

Or fetch the API spec:

```bash
curl -s http://localhost:8090/v3/api-docs | jq '.paths | keys'
# Expected: ["/v1/payment", "/v1/payment/{id}"]
```

### 6.7 Grafana Dashboards

Open in browser: http://localhost:3000

- No login required (anonymous access enabled)
- Pre-provisioned dashboards should be available under the Dashboards menu
- Look for a payment gateway dashboard with panels for request rate, error rate, latency, and circuit breaker state

### 6.8 Distributed Tracing (Tempo)

After making a few payment requests, check traces in Grafana:

1. Open http://localhost:3000
2. Navigate to **Explore** > select **Tempo** data source
3. Search for traces — you should see spans for the payment processing flow

Or query Tempo directly:

```bash
curl -s http://localhost:3200/api/search | jq .
```

---

## 7. Cleanup

```bash
# Stop the Spring Boot app (Ctrl+C in the bootRun terminal)

# Stop and remove all containers
docker compose down

# Remove volumes too (full cleanup)
docker compose down -v
```

---

## Quick Reference: Test Card Numbers

| Last Digit | Bank Response | Gateway Status | HTTP Code |
|------------|--------------|----------------|-----------|
| 1, 3, 5, 7, 9 | `authorized: true` | `Authorized` | 201 |
| 2, 4, 6, 8 | `authorized: false` | `Declined` | 201 |
| 0 | 503 error | Bank error | 502 |

## Quick Reference: Supported Currencies

`GBP`, `USD`, `EUR`

## Quick Reference: Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| `cardNumber` | String | Required. 14-19 numeric digits only (`^\d{14,19}$`). Must pass Luhn check. |
| `expiryMonth` | Integer | Required. 1-12 |
| `expiryYear` | Integer | Required. >= 2024, must be in the future with `expiryMonth` |
| `currency` | String | Required. One of: `GBP`, `USD`, `EUR` |
| `amount` | Integer | Required. >= 1 (minor currency units, e.g., cents/pence) |
| `cvv` | String | Required. 3-4 numeric digits only (`^\d{3,4}$`) |
