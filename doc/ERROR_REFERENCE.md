# Error Reference

Complete reference of all error responses returned by the Payment Gateway API.

## Error Response Formats

### ErrorResponse

Used for non-validation errors (404, 429, 502, 500).

```json
{
  "message": "Bank service unavailable",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-02-13T12:00:00Z"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `message` | string | Human-readable error description |
| `correlationId` | string | Request correlation ID (from `X-Correlation-Id` header or auto-generated) |
| `timestamp` | string (ISO 8601) | When the error occurred |

### ValidationErrorResponse

Used for validation failures (400 Bad Request).

```json
{
  "status": "Rejected",
  "message": "Validation failed",
  "errors": [
    { "field": "cardNumber", "message": "Card number failed Luhn check" }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `status` | string | Always `"Rejected"` |
| `message` | string | Always `"Validation failed"` |
| `errors` | array | List of field-level validation errors |
| `errors[].field` | string | Name of the invalid field |
| `errors[].message` | string | Description of the validation failure |

## HTTP Status Codes

| Status | Meaning | Response Schema | When |
|--------|---------|-----------------|------|
| **201 Created** | Payment processed | `ProcessPaymentResponse` | Authorized or Declined (includes `Location` header) |
| **400 Bad Request** | Validation failed | `ValidationErrorResponse` | Invalid input — payment REJECTED and NOT stored |
| **404 Not Found** | Payment not found | `ErrorResponse` | No payment exists with the given ID |
| **429 Too Many Requests** | Rate limited | `ErrorResponse` | Per-IP rate limit exceeded (includes `Retry-After` header) |
| **502 Bad Gateway** | Bank unavailable | `ErrorResponse` | Bank communication failure or circuit breaker open |
| **500 Internal Server Error** | Unexpected error | `ErrorResponse` | Unhandled server error |

## Validation Rules

All validation failures return `400 Bad Request` with a `ValidationErrorResponse`.

### Bean Validation (checked before business logic)

| Field | Rule | Error Message |
|-------|------|---------------|
| `cardNumber` | Required, 14-19 digits, numeric only (`^\d{14,19}$`) | `"size must be between 14 and 19"` or `"must match \"^\d{14,19}$\""` |
| `expiryMonth` | Required, 1-12 | `"must be greater than or equal to 1"` / `"must be less than or equal to 12"` |
| `expiryYear` | Required, >= 2024 | `"must be greater than or equal to 2024"` |
| `currency` | Required, one of `GBP`, `USD`, `EUR` | `"Invalid value. Accepted values are: GBP, USD, EUR"` |
| `amount` | Required, >= 1 | `"must be greater than or equal to 1"` |
| `cvv` | Required, 3-4 digits, numeric only (`^\d{3,4}$`) | `"size must be between 3 and 4"` or `"must match \"^\d{3,4}$\""` |

### Business Validation (checked after bean validation passes)

| Field | Rule | Error Message |
|-------|------|---------------|
| `cardNumber` | Must pass Luhn checksum | `"Card number failed Luhn check"` |
| `expiryDate` | Combined month/year must be in the future | `"Card expiry date must be in the future"` |

### Malformed JSON

| Condition | Field | Error Message |
|-----------|-------|---------------|
| Unparseable JSON body | `requestBody` | `"Malformed JSON request body"` |
| Invalid currency value | `currency` | `"Invalid value. Accepted values are: GBP, USD, EUR"` |
| Invalid field type | (field name) | Jackson-specific parse error message |

## Error Constants

Defined in `ValidationErrors.java`:

| Constant | Value | Used For |
|----------|-------|----------|
| `VALIDATION_FAILED` | `"Validation failed"` | All `ValidationErrorResponse.message` |
| `MALFORMED_JSON` | `"Malformed JSON request body"` | Unparseable request bodies |
| `FIELD_REQUEST_BODY` | `"requestBody"` | Malformed JSON field name |
| `FIELD_CARD_NUMBER` | `"cardNumber"` | Luhn validation failures |
| `FIELD_EXPIRY_DATE` | `"expiryDate"` | Expiry date validation failures |
| `FIELD_CURRENCY` | `"currency"` | Currency deserialization failures |
| `CARD_NUMBER_INVALID_LUHN` | `"Card number failed Luhn check"` | Luhn checksum failure |
| `EXPIRY_DATE_IN_FUTURE` | `"Card expiry date must be in the future"` | Expired card |
| `CURRENCY_INVALID` | `"Invalid value. Accepted values are: GBP, USD, EUR"` | Invalid currency |

## Troubleshooting

### 400 Bad Request

**Cause:** The request body failed validation.

**Action:** Check the `errors` array for specific field failures. Fix the indicated fields and retry.

### 404 Not Found

**Cause:** No payment exists with the given UUID. This can occur if:
- The ID is incorrect
- The payment was REJECTED (rejected payments are not stored)
- The server was restarted (in-memory storage)

**Action:** Verify the payment ID. Note that REJECTED payments never get stored.

### 429 Too Many Requests

**Cause:** Per-IP rate limit exceeded.

**Action:** Wait for the number of seconds indicated in the `Retry-After` response header before retrying. Separate rate limits apply to GET and POST endpoints.

### 502 Bad Gateway

**Cause:** The acquiring bank is unreachable. The gateway retries up to 3 times with exponential backoff before returning this error. If the circuit breaker is open, the request fails immediately.

**Action:** Use the `correlationId` from the response body to trace the failure in gateway logs. Retry after a delay. If the error persists, the bank may be experiencing an outage.

### 500 Internal Server Error

**Cause:** An unexpected server error occurred.

**Action:** Use the `correlationId` from the response body to report the issue. The gateway logs will contain the full stack trace.
