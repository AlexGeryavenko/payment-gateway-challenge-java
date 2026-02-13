# Payment Gateway — Implementation Plan

## Table of Contents

- [1. Task Overview](#1-task-overview)
  - [1.1 Standards & Conventions](#11-standards--conventions)
- [2. Requirements](#2-requirements)
  - [2.1 Functional Requirements](#21-functional-requirements)
  - [2.2 Non-Functional Requirements](#22-non-functional-requirements)
- [3. Analysis](#3-analysis)
  - [3.1 Existing Code Audit](#31-existing-code-audit)
  - [3.2 Dependencies Analysis](#32-dependencies-analysis)
  - [3.3 3rd Party Dependencies & SLA](#33-3rd-party-dependencies--sla)
  - [3.4 Toolchain Migration](#34-toolchain-migration)
- [4. Cover Existing Code with Tests](#4-cover-existing-code-with-tests)
- [5. MVP — Functional Requirements](#5-mvp--functional-requirements)
- [6. MVP — Non-Functional Requirements](#6-mvp--non-functional-requirements)
  - [6.1 Observability & Logging](#61-observability--logging-nfr-5)
  - [6.2 Metrics & Grafana Dashboards](#62-metrics--grafana-dashboards-nfr-6-nfr-7)
  - [6.3 Distributed Tracing](#63-distributed-tracing-nfr-8)
  - [6.4 Alerting](#64-alerting-nfr-9)
  - [6.5 Security — PCI DSS](#65-security--pci-dss-nfr-1-nfr-2-nfr-3)
  - [6.6 Resilience — Circuit Breaker & Rate Limiting](#66-resilience--circuit-breaker--rate-limiting-nfr-11-nfr-12)
  - [6.7 Performance Testing](#67-performance-testing-nfr-13)
  - [6.8 Code Quality — OWASP & SonarQube](#68-code-quality--owasp--sonarqube-nfr-16-nfr-17)
  - [6.9 Environment Configuration — .env + spring-dotenv](#69-environment-configuration--env--spring-dotenv-nfr-18)
  - [6.10 CI/CD Pipeline — GitHub Actions](#610-cicd-pipeline--github-actions-nfr-19)
  - [6.11 Quality Gates — Checkstyle, JaCoCo, SpotBugs+FindSecBugs](#611-quality-gates--checkstyle-jacoco-spotbugsfindsecbugs-nfr-20)
  - [6.12 Branch Protection — GitHub Configuration](#612-branch-protection--github-configuration-nfr-21)
  - [6.13 GitHub Badges](#613-github-badges-nfr-22)
- [7. Post-MVP Scope](#7-post-mvp-scope)
  - [7.1 Hexagonal Architecture Refactoring](#71-hexagonal-architecture-refactoring)
  - [7.2 ISO 20022 Banking Contracts](#72-iso-20022-banking-contracts)
  - [7.3 mTLS for Bank Communication](#73-mtls-for-bank-communication)
  - [7.4 Additional Production Improvements](#74-additional-production-improvements)
- [Appendix A: C4 Architecture Diagrams](#appendix-a-c4-architecture-diagrams)
- [Appendix B: 3rd Party Contracts (OpenAPI)](#appendix-b-3rd-party-contracts-openapi)

### Diagrams

All architecture diagrams are in PlantUML format in [`doc/diagram/`](diagram/):

| File | Description |
|---|---|
| [`c4-level1-context.puml`](diagram/c4-level1-context.puml) | System context — Merchant, Gateway, Bank |
| [`c4-level2-container.puml`](diagram/c4-level2-container.puml) | Containers — App, Store, Simulator, Observability |
| [`c4-level3-component.puml`](diagram/c4-level3-component.puml) | Components — Hexagonal architecture (Post-MVP target) |
| [`c4-level4-sequence.puml`](diagram/c4-level4-sequence.puml) | Sequence — Payment processing flow with all outcomes |

---

# 1. Task Overview

Build a Payment Gateway — a Spring Boot REST API that allows merchants to:
- Submit card payments, which are validated, forwarded to an acquiring bank simulator, and stored in memory
- Retrieve previously made payments by ID

The codebase is a partially scaffolded Spring Boot 3.1.5 / Java 17 project. A `GET /payment/{id}` endpoint exists and partially works. The `POST /payment` endpoint, input validation, bank integration, and production-grade non-functional requirements are missing.

**Target stack**: Java 25 LTS, Spring Boot 4.0.2, Spring Framework 7, Gradle 9, Jakarta EE 11.

**API-first**: All 3rd party and our own contracts defined as OpenAPI 3.1 specs in `doc/openapi/`. Models and API interfaces generated via `openapi-generator-gradle-plugin`.

**Ports**: App :8090, Bank simulator :8080, Mountebank admin :2525.

---

### Implementation Status

| Sprint | Status | Version | PAYGATE Items | Commits | Key Deviation |
|--------|--------|---------|---------------|---------|---------------|
| Sprint 1 — Planning | COMPLETED | `v0.0.0` | PAYGATE-1 | 3 | None |
| Sprint 2 — Functional MVP | COMPLETED | `v0.1.0` | PAYGATE-2 through PAYGATE-11 | 12 | Stayed on Java 17 / Boot 3.1.5 (toolchain migration deferred) |
| Sprint 3 — Non-Functional MVP | COMPLETED | `v0.2.0` | PAYGATE-12 through PAYGATE-19 | 10 | Sections 6.7, 6.10, 6.12, 6.13 deferred to Sprint 4 |
| Sprint 4 — Post-MVP | NOT STARTED | `v0.3.0` | PAYGATE-20 through PAYGATE-28 | — | Planned |

> **Toolchain deviation**: The plan targets Java 25 / Spring Boot 4.0.2 / Gradle 9 (Section 3.4). The actual implementation stayed on **Java 17 / Spring Boot 3.1.5 / Gradle 8.10.2** because the toolchain migration was deferred — all functional and non-functional requirements were achievable on the existing stack. Section 3.4 remains as-is for reference.
---

## 1.1 Standards & Conventions

All code, API design, and development workflow follow industry standards. Every design decision in this plan references the applicable standard.

### HTTP Protocol

| Standard | Scope | How We Use It |
|---|---|---|
| [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html) | Status codes, methods, headers, content negotiation | **Primary reference** for all HTTP behavior. Defines the semantics of status codes (200, 400, 404, 429, 502), methods (GET, POST), and headers (`Content-Type`, `Retry-After`) used throughout this API |
| [RFC 2616 — HTTP/1.1](https://datatracker.ietf.org/doc/html/rfc2616) | Original HTTP/1.1 spec (1999) | Historical reference. **Obsoleted** by RFCs 7230-7235 (2014), which were further obsoleted by RFCs 9110-9112 (2022). Referenced here for completeness; RFC 9110 is the authoritative source |
| [RFC 2068 — HTTP/1.1](https://datatracker.ietf.org/doc/html/rfc2068) | Original HTTP/1.1 spec (1997) | Historical foundation. **Obsoleted** by RFC 2616. The original standard that established the HTTP/1.1 protocol semantics we still follow today |
| [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html) | Error response format | Considered for error responses. Current design uses simple `{"message": "..."}` for consistency with existing tests. Post-MVP could adopt `application/problem+json` |

**HTTP status codes used in this API** (per RFC 9110 Section 15):

| Code | RFC 9110 Section | Our Usage | Requirement |
|---|---|---|---|
| `200 OK` | 15.3.1 | Successful payment processing (Authorized or Declined) | **FR-1.1**, **FR-1.2** |
| `200 OK` | 15.3.1 | Successful payment retrieval | **FR-2** |
| `400 Bad Request` | 15.5.1 | "The server cannot or will not process the request due to something that is perceived to be a client error" — validation failure | **FR-1.3** |
| `404 Not Found` | 15.5.5 | "The origin server did not find a current representation for the target resource" — payment ID not found | **FR-2** |
| `429 Too Many Requests` | (RFC 6585 §4) | Rate limit exceeded. Includes `Retry-After` header (RFC 9110 §10.2.3) | **NFR-12** |
| `502 Bad Gateway` | 15.6.3 | "The server, while acting as a gateway or proxy, received an invalid response from an inbound server" — bank simulator 503 | **NFR-15** |

### REST API Design

| Standard | Scope | How We Use It |
|---|---|---|
| [Richardson Maturity Model](https://martinfowler.com/articles/richardsonMaturityModel.html) | REST maturity levels | Our API targets **Level 2** (see below) |
| [OpenAPI 3.1](https://spec.openapis.org/oas/v3.1.0) | API specification format | All contracts defined in `doc/openapi/`. Models and interfaces generated from specs |
| [ISO 4217](https://www.iso.org/iso-4217-currency-codes.html) | Currency codes | Validate against GBP, USD, EUR (**FR-6**) |

**Richardson Maturity Model — Target Level 2**:

| Level | Description | Our Status |
|---|---|---|
| Level 0 — Swamp of POX | Single endpoint, HTTP as transport tunnel | N/A |
| Level 1 — Resources | Individual URIs per resource (`/payment`, `/payment/{id}`) | **Implemented** |
| Level 2 — HTTP Verbs | Proper use of GET (safe, idempotent query), POST (state-changing), correct status codes per RFC 9110 | **Target** — our MVP scope |
| Level 3 — Hypermedia (HATEOAS) | Response links describe available actions, server-driven navigation | **Post-MVP** — could add `_links` for payment status transitions |

Level 2 compliance checklist for our API:

| RMM Level 2 Criterion | Implementation |
|---|---|
| `GET` is safe and idempotent | `GET /payment/{id}` — no side effects, cacheable |
| `POST` for state changes | `POST /payment` — creates a new payment resource |
| Proper status codes | 200, 400, 404, 429, 502 per RFC 9110 (not just 200 for everything) |
| `Content-Type` headers | `application/json` for all requests and responses |
| Error responses with context | Structured error body (`{"message": "..."}`) for 400, 404, 429, 502 |

### Git Conventions

| Standard | Scope | How We Use It |
|---|---|---|
| [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) | Commit message format | All commits follow the format below |

**Commit message format**:
```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**Allowed types**:

| Type | SemVer | When to Use |
|---|---|---|
| `feat` | MINOR | New feature (e.g., POST endpoint, bank integration) |
| `fix` | PATCH | Bug fix (e.g., card number type, expiry date format) |
| `refactor` | — | Code restructuring without behavior change (e.g., hexagonal migration) |
| `test` | — | Adding or updating tests |
| `docs` | — | Documentation changes (PLAN.md, OpenAPI specs, README) |
| `build` | — | Build system changes (Gradle, plugins, dependencies) |
| `ci` | — | CI/CD pipeline changes |
| `perf` | — | Performance improvements |
| `style` | — | Code style changes (formatting, no logic change) |
| `chore` | — | Maintenance tasks (dependency updates, cleanup) |

**Breaking changes**: `feat!:` or `BREAKING CHANGE:` footer → MAJOR version bump.

**Scope examples**: `feat(payment): add POST endpoint`, `fix(model): change card number type to String`, `build(gradle): migrate to Spring Boot 4.0.2`.

# 2. Requirements

## 2.1 Functional Requirements

| ID | Requirement | Detail |
|---|---|---|
| **FR-1** | Process a payment (`POST /payment`) | Merchant submits card payment. Gateway validates, calls acquiring bank, returns result. RMM Level 2: POST for state change |
| **FR-1.1** | Authorized outcome | Bank approved. Store payment. Return `200 OK` (RFC 9110 §15.3.1) with payment details |
| **FR-1.2** | Declined outcome | Bank rejected. Store payment. Return `200 OK` (RFC 9110 §15.3.1) with payment details |
| **FR-1.3** | Rejected outcome | Validation failed. Do NOT call bank. Do NOT store. Return `400 Bad Request` (RFC 9110 §15.5.1) |
| **FR-2** | Retrieve a payment (`GET /payment/{id}`) | Merchant queries by payment ID. Return stored payment details or `404 Not Found` (RFC 9110 §15.5.5). RMM Level 2: GET is safe, idempotent |
| **FR-3** | Input validation — Card number | Required. 14-19 chars. Numeric only |
| **FR-4** | Input validation — Expiry month | Required. Value 1-12 |
| **FR-5** | Input validation — Expiry year | Required. Month+Year combination must be in the future |
| **FR-6** | Input validation — Currency | Required. Exactly 3 chars. Valid ISO 4217. Validate against max 3 codes (GBP, USD, EUR) |
| **FR-7** | Input validation — Amount | Required. Positive integer. Minor currency units (cents) |
| **FR-8** | Input validation — CVV | Required. 3-4 chars. Numeric only |
| **FR-9** | Response fields (POST and GET) | `id` (UUID), `status` (Authorized/Declined), `cardNumberLastFour` (last 4 digits only), `expiryMonth`, `expiryYear`, `currency`, `amount` |
| **FR-10** | Card number masking | Never return full card number. Only last 4 digits — PCI compliance |
| **FR-11** | Bank integration | Forward validated payment to acquiring bank (`POST http://localhost:8080/payments`), map response to Authorized/Declined |
| **FR-12** | Payment storage | Store authorized/declined payments in memory. Rejected payments are NOT stored |

## 2.2 Non-Functional Requirements

| ID | Requirement | Detail |
|---|---|---|
| **NFR-1** | PCI DSS — PAN masking | Never return, store, or log full card number. Only last 4 digits (PCI DSS Req 3.4) |
| **NFR-2** | PCI DSS — CVV not stored | CVV must not be persisted after authorization (PCI DSS Req 3.2) |
| **NFR-3** | PCI DSS — Log masking | Card number patterns must be masked in all log output (PCI DSS Req 3.4) |
| **NFR-4** | Thread safety | Repository must support concurrent requests (production readiness) |
| **NFR-5** | Structured logging | JSON-formatted logs for production, human-readable for dev. MDC fields: correlationId, paymentId |
| **NFR-6** | Metrics (Prometheus) | Custom business metrics: payment totals by status, duration, bank call success/error |
| **NFR-7** | Grafana dashboards | Environment metrics (JVM, HTTP) and business metrics (payment rates, outcomes, bank health) |
| **NFR-8** | Distributed tracing (OpenTelemetry) | Trace spans for validation, bank call, storage. Correlation ID propagation |
| **NFR-9** | Alerting rules | Bank error rate >5%, rejection rate >30%, HTTP 5xx >1%, p99 latency >5s, heap >85% |
| **NFR-10** | Health check endpoint | `/actuator/health` returning `{"status":"UP"}` |
| **NFR-11** | Circuit breaker | Fail-fast when bank simulator is unavailable. Prevent cascade failures |
| **NFR-12** | Rate limiting | Per-merchant throttling to protect against abuse / DoS. `429 Too Many Requests` (RFC 6585 §4) with `Retry-After` header (RFC 9110 §10.2.3) |
| **NFR-13** | Performance SLA | See Section 3.3 for SLA targets and performance testing plan |
| **NFR-14** | Automated test coverage | Test pyramid: unit, integration, contract, E2E. All FR and NFR covered |
| **NFR-15** | Bank error handling | Bank 503 → return 502 Bad Gateway to merchant. No payment stored |
| **NFR-16** | OWASP dependency check | Scan all dependencies for known CVEs (OWASP Top 10 A06:2021 — Vulnerable and Outdated Components). Fail build on CVSS ≥ 7 |
| **NFR-17** | Static analysis (SonarQube) | Continuous code quality — bugs, code smells, security hotspots, test coverage. Quality gate: 0 bugs, 0 vulnerabilities, ≥ 80% coverage on new code |
| **NFR-18** | Environment configuration | Externalize all environment-specific config via `.env` files using spring-dotenv. No secrets in source control |
| **NFR-19** | CI/CD pipeline | GitHub Actions workflow: build, test, and enforce all quality gates on every push and PR to main |
| **NFR-20** | Quality gates | Checkstyle (code style), JaCoCo (≥80% coverage), SpotBugs+FindSecBugs (static analysis + security), OWASP (dependency CVEs). Enforced locally via Gradle and in CI |
| **NFR-21** | Branch protection | Prevent direct commits/merges to main. Require PR reviews + all CI checks passing before merge |
| **NFR-22** | GitHub badges | Visible build status, coverage, quality, and security badges on README for at-a-glance project health |

---

# 3. Analysis

## 3.1 Existing Code Audit

### File Inventory

| # | File | Pkg | Lines | Status | Violates |
|---|---|---|---|---|---|
| 1 | `PaymentGatewayApplication.java` | root | 10 | OK | — |
| 2 | `ApplicationConfiguration.java` | configuration | 19 | OK | Configured `RestTemplate` with 10s timeouts but never injected — dead config (**FR-11**) |
| 3 | `PaymentGatewayController.java` | controller | 25 | Partial | Only `GET /payment/{id}`. No POST endpoint (**FR-1**). Returns `PostPaymentResponse` instead of `GetPaymentResponse` (**FR-9**) |
| 4 | `PaymentGatewayService.java` | service | 31 | Partial | `processPayment()` is dead stub — returns `UUID.randomUUID()`, no bank call (**FR-11**), no validation (**FR-3..FR-8**), no storage (**FR-12**) |
| 5 | `PaymentsRepository.java` | repository | 22 | Works | `HashMap<UUID, PostPaymentResponse>` — not thread-safe (**NFR-4**). Stores web DTOs instead of domain entities |
| 6 | `PostPaymentRequest.java` | model | 82 | **Broken** | 6 critical bugs — see below |
| 7 | `PostPaymentResponse.java` | model | 84 | Minor bugs | `toString()` says "GetPaymentResponse" (copy-paste). `cardNumberLastFour` is `int` — loses leading zeros (**FR-10**) |
| 8 | `GetPaymentResponse.java` | model | 83 | Unused | Not referenced by any controller or service (**FR-9**) |
| 9 | `PaymentStatus.java` | enums | 20 | OK | — |
| 10 | `ErrorResponse.java` | model | 19 | OK | — |
| 11 | `EventProcessingException.java` | exception | 7 | OK | — |
| 12 | `CommonExceptionHandler.java` | exception | 22 | OK | Hardcodes "Page not found" — ignores actual exception (**NFR-15**) |

**Total: 11 source files, ~400 lines. ~30% functional.**

### Critical Bugs in `PostPaymentRequest.java`

| # | Bug | Impact | Violates |
|---|---|---|---|
| 1 | Field named `cardNumberLastFour` (`int`) with `@JsonProperty("card_number_last_four")` | Merchant must send FULL card number (14-19 digits). Bank expects `card_number`. Name and type are wrong | **FR-3**, **FR-11** |
| 2 | `int` type for card number | `int` max is ~2.1 billion (10 digits). Card numbers are 14-19 digits — integer overflow. Leading zeros lost | **FR-3** |
| 3 | `int` type for CVV | CVV "0123" becomes `123`. Leading zeros lost. CVV must be `String` | **FR-8** |
| 4 | `getExpiryDate()`: `String.format("%d/%d", ...)` | Produces "4/2025" not "04/2025". Bank expects zero-padded `MM/YYYY` format | **FR-11** |
| 5 | `toString()` exposes card number and CVV in plaintext | If logged, full PAN and CVV visible | **NFR-1**, **NFR-2**, **NFR-3** |
| 6 | `implements Serializable` with no `serialVersionUID` | Minor — compiler warning, fragile serialization contract | — |

### Architecture Issues

| Issue | Detail | Violates |
|---|---|---|
| No interfaces anywhere | Controller → concrete Service → concrete Repository. Zero abstractions. Untestable in isolation | **NFR-14** |
| DTO leak into persistence | `PaymentsRepository` stores `PostPaymentResponse` (web-layer DTO) as its entity | — |
| Dead code | `GetPaymentResponse.java` exists but nothing uses it. `processPayment()` stub returns random UUID | **FR-9** |
| No bank integration | `RestTemplate` bean is configured but never injected or used | **FR-11** |
| No input validation | Zero validation at any layer — controller, service, or model | **FR-3..FR-8** |
| Single exception type | `EventProcessingException` handles everything. Handler hardcodes "Page not found" | **NFR-15** |

### Existing Test Coverage

```
src/test/java/.../controller/PaymentGatewayControllerTest.java
├── whenPaymentWithIdExistThenCorrectPaymentIsReturned    @SpringBootTest + MockMvc, seeds repo, GET, asserts JSON
└── whenPaymentWithIdDoesNotExistThen404IsReturned        random UUID, asserts 404 + {"message":"Page not found"}
```

**Coverage gaps**: No service tests, no repository tests, no error edge cases, no POST tests, no content-type validation.

**Contract locked by tests**: JSON response uses camelCase (`$.cardNumberLastFour`, `$.expiryMonth`).

### Build Configuration (`build.gradle`)

**Current state**:
```
plugins:
  java
  org.springframework.boot          3.1.5
  io.spring.dependency-management   1.0.15.RELEASE

java.sourceCompatibility = '17'

dependencies:
  implementation:
    spring-boot-starter-web                 ← REST + Tomcat + Jackson
    springdoc-openapi-starter-webmvc-ui     ← Swagger UI (2.2.0, explicit version)
  testImplementation:
    spring-boot-starter-test                ← JUnit 5, Mockito, MockMvc, AssertJ

test:
  useJUnitPlatform()
```

| What's there | What's missing | Violates |
|---|---|---|
| `spring-boot-starter-web` — provides REST, embedded Tomcat, Jackson JSON | `spring-boot-starter-validation` — no `@Valid`, no bean validation | **FR-3..FR-8** |
| `springdoc-openapi-starter-webmvc-ui:2.2.0` — Swagger UI at `/swagger-ui/index.html` | `spring-boot-starter-actuator` — no health, no metrics, no Prometheus | **NFR-6**, **NFR-10** |
| `spring-boot-starter-test` — JUnit 5 + Mockito + MockMvc | No Resilience4j, no micrometer-tracing, no k6 integration | **NFR-11**, **NFR-8** |
| Java 17 source compatibility | Java 25 features (records, scoped values, compact source files, flexible constructors) unavailable | — |
| Spring Boot 3.1.5 | EOL since Nov 2024 — no security patches. Spring Boot 4.0.2 is current | — |
| springdoc 2.2.0 | Incompatible with Boot 4. springdoc 3.0.1+ required for Boot 4 | — |
| `io.spring.dependency-management` plugin | Removed in Boot 4 — use Gradle's native `platform()` BOM support | — |
| No OpenAPI code generation | No `openapi-generator-gradle-plugin` — models and API stubs are hand-written | — |

**Note**: `RestTemplate` is provided by `spring-boot-starter-web` but requires manual bean configuration (done in `ApplicationConfiguration.java` — 10s timeouts). It is never injected into any service (**FR-11**). **RestTemplate is deprecated in Spring Framework 7** — migrate to `RestClient`.

### Docker Compose (`docker-compose.yml`)

```yaml
version: "3.8"
services:
  bank_simulator:
    image: bbyars/mountebank:2.8.1
    ports:
      - "2525:2525"    # Mountebank admin API
      - "8080:8080"    # Bank simulator endpoint
    command: --configfile /imposters/bank_simulator.ejs --allowInjection
    volumes:
      - ./imposters:/imposters
```

| Aspect | Detail |
|---|---|
| **Image** | `bbyars/mountebank:2.8.1` — official Mountebank image. **Do not upgrade** |
| **Port 8080** | Bank simulator endpoint (`POST /payments`). This is the 3rd-party dependency our gateway calls (**FR-11**) |
| **Port 2525** | Mountebank admin API. Used to inspect/manage imposters. Not used by our app |
| **`--allowInjection`** | Enables JavaScript injection in stubs (used for `authorization_code` UUID generation) |
| **Volume** | Mounts local `imposters/` directory containing `bank_simulator.ejs` |
| **Missing** | No app service — gateway runs on host via `./gradlew bootRun`. No observability stack |
| **Constraint** | **Do not modify** `docker-compose.yml` or `imposters/bank_simulator.ejs` |

### Bank Simulator Internals (`imposters/bank_simulator.ejs`)

The file is a Mountebank imposter configuration (JSON + EJS template for UUID generation). It defines 4 stubs evaluated **in order** (first match wins):

**Stub 1: Missing fields → 400**
```
Predicate: POST /payments AND (card_number missing OR expiry_date missing
           OR currency missing OR amount missing OR cvv missing)
Response:  400 { "error_message": "Not all required properties were sent in the request" }
```
Uses `"exists": {"body": {"card_number": false}}` — checks field **absence**, not empty values. Sending `"card_number": ""` passes this check.

**Stub 2: Card ends in odd digit (1,3,5,7,9) → Authorized**
```
Predicate: POST /payments AND card_number endsWith 1|3|5|7|9
Response:  200 { "authorized": true, "authorization_code": "<random-uuid>" }
Behavior:  JavaScript decorate generates UUID via Math.random() (v4-like format)
```

**Stub 3: Card ends in even digit (2,4,6,8) → Declined**
```
Predicate: POST /payments AND card_number endsWith 2|4|6|8
Response:  200 { "authorized": false, "authorization_code": "" }
```
Note: `authorization_code` is empty string, not null or absent.

**Stub 4: Card ends in 0 → 503 Service Unavailable**
```
Predicate: POST /payments AND card_number endsWith 0
Response:  503 {}
```
Empty body. No error message. Simulates bank infrastructure failure.

**Default response** (no stub matches):
```
400 { "errorMessage": "The request supplied is not supported by the simulator" }
```
Note: Default uses `errorMessage` (camelCase), while Stub 1 uses `error_message` (snake_case) — inconsistent but irrelevant for our gateway.

**Key observations for implementation**:

| Observation | Impact on Gateway | Covers |
|---|---|---|
| Simulator checks field **presence** only, not format | We must validate all field formats ourselves (card length, expiry future, CVV digits, etc.) | **FR-3..FR-8** |
| `expiry_date` must be `"MM/YYYY"` format | Gateway receives separate `expiry_month` + `expiry_year`, must format to `String.format("%02d/%d", month, year)` | **FR-11** |
| `card_number` is matched by **last character** only | Any string ending in 1-9 or 0 triggers the corresponding stub — simulator doesn't validate card number length | **FR-3** |
| `cvv` field is required (Stub 1 checks presence) | Must be included in bank request even though simulator doesn't validate format | **FR-8**, **FR-11** |
| `amount` is required but format isn't checked by simulator | Must be positive integer (minor units) — validated by gateway only | **FR-7** |
| 503 returns empty body `{}` | Gateway must handle empty body gracefully — cannot deserialize `authorized` field | **NFR-15** |
| UUID generation uses `Math.random()` (not crypto-secure) | Fine for simulator. Our payment `id` should use `UUID.randomUUID()` (Java's `SecureRandom`-backed) | **FR-9** |
| Stubs are order-dependent | Missing fields check comes first — if a required field is absent AND card ends in odd digit, response is 400, not 200 | — |

---

## 3.2 Dependencies Analysis

### Current vs Target Versions

| Dependency | Current | Target | Gap | Migration Risk |
|---|---|---|---|---|
| **Java** | 17 | **25 LTS** (Sep 2025) | 2 LTS versions behind (17→21→25) | Medium — backward compatible. Enables records, scoped values, compact source files, flexible constructors, module imports |
| **Gradle** | 8.2.1 | **9.x** | Major version. Boot 4 supports Gradle 8.14+ or Gradle 9 | Medium — Gradle 9 has breaking changes in task configuration, plugin API. Required for Boot 4 optimal support |
| **Spring Boot** | 3.1.5 | **4.0.2** (Jan 2026) | Major version. Based on Spring Framework 7, Jakarta EE 11, Servlet 6.1 | **High** — RestTemplate deprecated, `io.spring.dependency-management` removed, modularized auto-config, JSpecify null safety. Must migrate through 3.5 first |
| **spring-dependency-management** | 1.0.15.RELEASE | **Removed** | Plugin eliminated in Boot 4 — use Gradle's native `platform()` BOM | Medium — must refactor all `build.gradle` dependency declarations |
| **springdoc-openapi** | 2.2.0 | **3.0.1** | Major version. springdoc 2.x incompatible with Boot 4 (Jackson 3, modular auto-config) | Medium — API mostly the same, just version bump |
| **spring-boot-starter-test** | (managed by Boot) | (managed by Boot 4) | Auto-updates. `TestRestTemplate` requires `@AutoConfigureTestRestTemplate`. New `RestTestClient` available | Low-Medium |
| **Mountebank** (Docker) | 2.8.1 | 2.8.1 | Current | None — do not touch |
| **openapi-generator-gradle-plugin** | Not present | **7.19.0** | New addition — generates models + API from OpenAPI specs | Low — additive, no existing code changes |

### Key Spring Boot 4 Breaking Changes

| Change | Impact | Action |
|---|---|---|
| `RestTemplate` deprecated (Spring Framework 7) | `ApplicationConfiguration.java` uses `RestTemplate` bean | Migrate to `RestClient` (synchronous, fluent API). Support until 2029 but prefer modern API |
| `io.spring.dependency-management` removed | `build.gradle` uses this plugin | Replace with `platform(SpringBootPlugin.BOM_COORDINATES)` in dependencies block |
| Modularized auto-configuration | Single `spring-boot-autoconfigure` jar split into focused modules | May need additional module dependencies — verify with `./gradlew dependencies` |
| Jakarta EE 11 / Servlet 6.1 | `javax.*` → `jakarta.*` (already done in Boot 3, but EE 11 adds new APIs) | Verify no `javax.*` imports remain |
| JSpecify null safety | `@Nullable` / `@NonNull` annotations from JSpecify | Informational — adopt gradually |
| `@HttpServiceClient` annotation | New way to call external services via annotated interfaces | Alternative to RestClient for bank adapter. Consider for Post-MVP |
| `TestRestTemplate` no longer auto-configured | Existing tests may break if they inject `TestRestTemplate` | Add `@AutoConfigureTestRestTemplate` or migrate to `RestTestClient` |

### Transitive Dependencies (via Spring Boot 4.0.2)

| Library | Provided By | Used For |
|---|---|---|
| Spring MVC 7.0.x | spring-boot-starter-web | REST controllers, request mapping |
| Embedded Tomcat 11.x | spring-boot-starter-web | HTTP server (Servlet 6.1) |
| Jackson 3.x | spring-boot-starter-web | JSON serialization/deserialization |
| SLF4J 2.x + Logback 1.5.x | spring-boot-starter-web | Logging |
| JUnit 5.11.x | spring-boot-starter-test | Test framework |
| Mockito 5.x | spring-boot-starter-test | Mocking |
| AssertJ 3.x | spring-boot-starter-test | Fluent assertions |
| MockMvc | spring-boot-starter-test | HTTP layer testing |
| RestClient | spring-boot-starter-web | Synchronous HTTP client (replaces RestTemplate) |

### Dependencies to Add

| Dependency | MVP Stage | Purpose |
|---|---|---|
| `spring-boot-starter-actuator` | NFR | `/actuator/health`, `/actuator/prometheus`, `/actuator/info` (**NFR-6**, **NFR-10**) |
| `spring-boot-starter-opentelemetry` | NFR | New in Boot 4 — auto-configures OTel SDK, OTLP export for metrics + traces (**NFR-8**) |
| `micrometer-registry-prometheus` | NFR | Prometheus metrics export (**NFR-6**) |
| `spring-boot-starter-validation` | FR | Jakarta Bean Validation (`@NotBlank`, `@Valid`) (**FR-3..FR-8**) |
| `resilience4j-spring-boot3` | NFR | Circuit breaker (**NFR-11**). Verify Boot 4 compatibility — may need `resilience4j-spring-boot` 2.3+ |
| `bucket4j-core` + `bucket4j-spring-boot-starter` | NFR | Rate limiting (**NFR-12**) |
| `org.openapitools:jackson-databind-nullable` | FR | Support for OpenAPI nullable fields in generated models |
| `openapi-generator-gradle-plugin:7.19.0` | FR | Code generation from OpenAPI specs (build-time only) |
| `org.owasp.dependencycheck` plugin (12.1.1) | NFR | OWASP dependency-check — scans transitive deps for known CVEs (NVD). Fails build on CVSS ≥ 7 (**NFR-16**) |
| `org.sonarqube` plugin (6.0.1.5171) | NFR | SonarQube integration — static analysis, coverage reporting, quality gate enforcement (**NFR-17**) |

### OpenAPI Contracts & Code Generation

All 3rd-party and our own API contracts are defined as OpenAPI 3.1 specs and used to **generate** models and API interfaces:

| Spec File | Generator | Generates | Package |
|---|---|---|---|
| `doc/openapi/bank-simulator.yaml` | `java` (library: `restclient`) | Client models + API interface for calling bank | `*.generated.bank.model`, `*.generated.bank.api` |
| `doc/openapi/payment-gateway.yaml` | `spring` (interfaceOnly: `true`) | Server interfaces + request/response DTOs | `*.generated.api`, `*.generated.model` |

**Generated code is NOT committed** — it lives in `$buildDir/generated/` and is regenerated on every build. Source of truth is always the YAML spec.

**What is generated vs hand-written**:

| Layer | Generated from spec | Hand-written |
|---|---|---|
| Bank client models (`BankPaymentRequest`, `BankPaymentResponse`) | Yes — from `bank-simulator.yaml` | — |
| Bank client API interface | Yes — with `restclient` library | Adapter impl wrapping generated client |
| Our API request/response DTOs (`ProcessPaymentRequest`, etc.) | Yes — from `payment-gateway.yaml` | — |
| Our API controller interface (`PaymentsApi`) | Yes — with `interfaceOnly: true` | Controller impl (`implements PaymentsApi`) |
| Domain model (`Payment`, value objects) | No | Yes — domain is spec-independent |
| Service / Use Case logic | No | Yes |
| Repository | No | Yes |

---

## 3.3 3rd Party Dependencies & SLA

### Bank Simulator (Acquiring Bank)

The only external dependency is the acquiring bank, simulated via Mountebank at `http://localhost:8080/payments`.

**Observed SLA from simulator** (no formal SLA document available):

| Parameter | Observed Value | Notes |
|---|---|---|
| Response time | <5ms (local Docker) | Not representative of production. Real bank latency is typically 200-2000ms |
| Availability | Deterministic (card ending 0 → 503) | Simulated failure, not random |
| Rate limit | None (mock) | No throttling on simulator |
| Timeout | N/A | `RestTemplate` configured with 10s connect + read timeout |

**Because no formal SLA is provided by the bank**, we must:

1. **Define our own SLA targets** — what we commit to deliver to merchants
2. **Validate through performance testing** — measure actual throughput in a controlled environment
3. **Implement resilience patterns** — circuit breaker, timeouts, retries to handle bank degradation

### Our SLA Targets (what we deliver to merchants)

| Metric | Target | Measured By | Alert Threshold |
|---|---|---|---|
| `POST /payment` p50 latency | < 200ms (excl. bank) | Micrometer timer (**NFR-6**) | p99 > 5s (**NFR-9**) |
| `POST /payment` p99 latency | < 1s (excl. bank) | Micrometer timer | — |
| `GET /payment/{id}` p50 latency | < 10ms | Micrometer timer | — |
| `GET /payment/{id}` p99 latency | < 50ms | Micrometer timer | — |
| Availability | 99.9% (excl. bank downtime) | Uptime monitor | HTTP 5xx > 1% (**NFR-9**) |
| Throughput | >= 500 req/s per instance | Performance test | — |
| Error rate (our faults) | < 0.1% | Prometheus counter | HTTP 5xx > 1% (**NFR-9**) |

**Note**: These targets exclude bank latency/errors. Bank degradation is handled by the circuit breaker (**NFR-11**) and surfaced to the merchant as 502.

### Circuit Breaker SLA (**NFR-11**)

| Parameter | Value | Rationale |
|---|---|---|
| Failure rate threshold | 50% | Open circuit when half of calls fail |
| Slow call threshold | 3s | Calls exceeding this count as slow |
| Slow call rate threshold | 80% | Open circuit when 80% of calls are slow |
| Wait duration in open state | 30s | Time before half-open probe |
| Permitted calls in half-open | 3 | Probe calls before deciding to close/reopen |
| Sliding window size | 10 calls | Recent call sample for failure rate calculation |

### Rate Limiting SLA (**NFR-12**)

| Parameter | Value | Rationale |
|---|---|---|
| Rate limit (POST /payment) | 100 req/s per merchant | Protects bank from overload, prevents abuse |
| Rate limit (GET /payment) | 500 req/s per merchant | Read-only, lighter load |
| Burst capacity | 2x sustained rate | Short spikes allowed |
| Response when limited | 429 Too Many Requests | Standard HTTP status |
| `Retry-After` header | Included | Tells merchant when to retry |

**Note**: In the current implementation, no merchant authentication exists. Rate limiting is per-IP as a proxy. Merchant-level rate limiting requires API key authentication (Post-MVP — Section 7.4).

### Performance Test Environment

Since there is no formal SLA from the bank and no production environment specs, we define a **reference environment** to produce reproducible, comparable results:

**Reference Environment**: Docker Compose on single host

| Component | Resource Limits | Image |
|---|---|---|
| Payment Gateway | 2 CPU, 512MB heap (`-Xmx512m`) | Custom (Dockerfile) |
| Bank Simulator | 1 CPU, 256MB | `mountebank:2.8.1` |
| Prometheus | 1 CPU, 256MB | `prom/prometheus:latest` |
| Grafana | 0.5 CPU, 128MB | `grafana/grafana:latest` |
| **Total host** | 4+ CPU, 2GB+ RAM | — |

**Docker Compose file**: `docker-compose.perf.yml`

```yaml
services:
  payment-gateway:
    build: .
    ports: ["8090:8090"]
    deploy:
      resources:
        limits:
          cpus: "2.0"
          memory: 768M
    environment:
      JAVA_OPTS: "-Xms256m -Xmx512m -XX:+UseZGC"
    depends_on: [bank-simulator]

  bank-simulator:
    image: mountebank/mountebank:2.8.1
    ports: ["8080:8080"]
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 256M
    volumes:
      - ./imposters:/imposters
    command: --configfile /imposters/bank_simulator.ejs --allowInjection

  prometheus:
    image: prom/prometheus:latest
    ports: ["9090:9090"]
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 256M
    volumes:
      - ./doc/prometheus:/etc/prometheus

  grafana:
    image: grafana/grafana:latest
    ports: ["3000:3000"]
    deploy:
      resources:
        limits:
          cpus: "0.5"
          memory: 128M
    volumes:
      - ./doc/grafana/provisioning:/etc/grafana/provisioning
      - ./doc/grafana/dashboards:/var/lib/grafana/dashboards
```

**Performance test deliverables** (what we produce from this environment):

| Deliverable | Tool | Format |
|---|---|---|
| Throughput report | k6 / Gatling | req/s at p50/p95/p99 latency |
| Latency distribution | k6 / Gatling | Histogram per endpoint |
| Saturation point | Ramp-up test | Max sustained req/s before p99 > 1s |
| Resource utilization | Prometheus + Grafana | CPU, memory, GC, threads under load |
| Soak test (1h) | k6 | Memory leak detection, GC behavior |
| Circuit breaker validation | k6 + bank simulator (card ending 0) | Verify circuit opens/closes correctly under bank failure |

---

## 3.4 Toolchain Migration

> **STATUS: NOT IMPLEMENTED** — Deferred indefinitely. All MVP requirements achieved on Java 17 / Boot 3.1.5.

Before any functional work, upgrade the toolchain. Characterization tests (Section 4) serve as safety net.

**Migration strategy**: Spring Boot recommends migrating through 3.5 first, then to 4.0. We do this in two phases.

### Migration Matrix

| Component | From | Phase 1 (interim) | Phase 2 (target) | Rationale |
|---|---|---|---|---|
| Java | 17 | **21 LTS** | **25 LTS** | Phase 1: unblock Boot 3.5. Phase 2: latest LTS (Sep 2025), scoped values, compact source files, flexible constructors |
| Gradle wrapper | 8.2.1 | **8.14** | **9.x** | Phase 1: minimum for Boot 4 on Gradle 8 line. Phase 2: full Boot 4 support, modern task API |
| Spring Boot | 3.1.5 | **3.5.3** | **4.0.2** | Phase 1: clean up deprecations. Phase 2: Spring Framework 7, Jakarta EE 11, RestClient |
| spring-dependency-management | 1.0.15.RELEASE | **1.1.7** | **Removed** | Phase 2: replaced by Gradle's native `platform()` BOM |
| springdoc-openapi | 2.2.0 | **2.8.9** | **3.0.1** | Phase 1: fix `NoSuchMethodError`. Phase 2: Boot 4 compatibility |
| RestTemplate | Used (dead config) | Injected + working | **→ RestClient** | Phase 2: RestTemplate deprecated in Spring Framework 7 |
| openapi-generator | Not present | Not present | **7.19.0** | Phase 2: generate models and API from OpenAPI specs |

### Phase 1: Migrate to Spring Boot 3.5.3 (safety net)

1. Update `gradle/wrapper/gradle-wrapper.properties` → `gradle-8.14-bin.zip`
2. Run `./gradlew wrapper` to regenerate wrapper jar
3. Update `build.gradle`: Boot 3.5.3, dep-management 1.1.7, Java 21, springdoc 2.8.9
4. Run `./gradlew clean test` — all characterization tests must pass
5. Fix any deprecation warnings (these become errors in Boot 4)

### Phase 2: Migrate to Spring Boot 4.0.2 + Java 25

1. Update Gradle wrapper → Gradle 9.x
2. Update `build.gradle`:
  - Remove `io.spring.dependency-management` plugin
  - Add `platform(SpringBootPlugin.BOM_COORDINATES)` to dependencies
  - Add `org.openapi.generator` plugin (7.19.0)
  - Boot 4.0.2, Java 25, springdoc 3.0.1
3. Replace `RestTemplate` bean with `RestClient` bean in `ApplicationConfiguration.java`
4. Add `@AutoConfigureTestRestTemplate` to tests using `TestRestTemplate` (or migrate to `RestTestClient`)
5. Add OpenAPI spec files (`doc/openapi/bank-simulator.yaml`, `doc/openapi/payment-gateway.yaml`)
6. Configure code generation tasks in `build.gradle` (see Section 3.1 target build.gradle)
7. Run `./gradlew clean test` — all tests must pass
8. Verify Swagger UI at `http://localhost:8090/swagger-ui/index.html`
9. Verify generated sources in `$buildDir/generated/`

---

# 4. Cover Existing Code with Tests

> **STATUS: COMPLETED** — Sprint 2, PAYGATE-2 (`a01645f`)

**Goal**: Lock current behavior before any changes. Michael Feathers' "Working Effectively with Legacy Code" approach.

**Prerequisite**: Existing code partially implements **FR-2** (GET endpoint works). API and business domain parts that exist must be covered.

### 4.1 Controller Characterization Tests

**File**: `src/test/java/.../controller/PaymentGatewayControllerCharacterizationTest.java`

Covers existing **FR-2** implementation:

| # | Test | Asserts | Covers |
|---|---|---|---|
| 1 | `GET /payment/{id}` happy path | 200, correct JSON shape, all 7 fields present, camelCase field names | **FR-2**, **FR-9** |
| 2 | `GET /payment/{id}` content-type | `application/json` header | **FR-9** |
| 3 | `GET /payment/{random-uuid}` not found | 404, `{"message":"Page not found"}` | **FR-2** |
| 4 | `GET /payment/not-a-uuid` bad format | 400 (Spring type mismatch) | **FR-2** |

### 4.2 Service Unit Tests

**File**: `src/test/java/.../service/PaymentGatewayServiceTest.java`

| # | Test | Asserts | Covers |
|---|---|---|---|
| 1 | `getPaymentById()` valid ID | Returns stored payment object | **FR-2** |
| 2 | `getPaymentById()` unknown ID | Throws `EventProcessingException("Invalid ID")` | **FR-2** |
| 3 | `processPayment()` stub | Returns non-null UUID (current stub behavior) | Documents current state of **FR-1** |

### 4.3 Repository Unit Tests

**File**: `src/test/java/.../repository/PaymentsRepositoryTest.java`

| # | Test | Asserts | Covers |
|---|---|---|---|
| 1 | `add()` + `get()` | Returns the stored payment | **FR-12** |
| 2 | `get()` unknown ID | Returns `Optional.empty()` | **FR-12** |
| 3 | Multiple payments | Each stored and retrieved independently | **FR-12** |

### Verification

```bash
./gradlew test   # ALL existing (2) + new characterization tests pass
```

Then apply toolchain migration (Section 3.4) and re-run:
```bash
./gradlew clean test   # Same tests pass on new versions
```

---

# 5. MVP — Functional Requirements

> **STATUS: COMPLETED** — Sprint 2, PAYGATE-3 through PAYGATE-11 (`85e8b88`..`6f7638d`)

**Goal**: Implement all functional requirements (**FR-1** through **FR-12**). Stay in the existing package structure (refactoring is Post-MVP).

### 5.1 Fix PostPaymentRequest

Fixes bugs identified in Section 3.1 that violate **FR-3**, **FR-8**, **FR-11**, **NFR-1**, **NFR-2**, **NFR-3**.

| Fix | Change | Resolves |
|---|---|---|
| Rename `cardNumberLastFour` → `cardNumber` | Full card number field, `@JsonProperty("card_number")` | Bug #1 → **FR-3**, **FR-11** |
| Change card number type | `int` → `String` | Bug #2 → **FR-3** |
| Change CVV type | `int` → `String` | Bug #3 → **FR-8** |
| Fix `getExpiryDate()` | `String.format("%02d/%d", ...)` for zero-padded month | Bug #4 → **FR-11** |
| Override `toString()` | Mask card number and CVV for PCI safety | Bug #5 → **NFR-1**, **NFR-3** |
| Remove `Serializable` | Not needed | Bug #6 |

### 5.2 Add POST Endpoint to Controller (**FR-1**)

Controller implements the generated `PaymentsApi` interface from `payment-gateway.yaml`:

```java
@RestController
public class PaymentController implements PaymentsApi {

    @Override
    public ResponseEntity<ProcessPaymentResponse> processPayment(ProcessPaymentRequest request) {
        ProcessPaymentResponse result = paymentGatewayService.processPayment(request);
        if (result.getStatus() == ProcessPaymentResponse.StatusEnum.REJECTED) {
            return ResponseEntity.badRequest().body(result);     // FR-1.3: 400
        }
        return ResponseEntity.ok(result);                        // FR-1.1/FR-1.2: 200
    }

    @Override
    public ResponseEntity<PaymentDetailsResponse> getPaymentById(UUID id) {
        // ... FR-2
    }
}
```

**Note**: `ProcessPaymentRequest`, `ProcessPaymentResponse`, `PaymentDetailsResponse` are **generated** from `doc/openapi/payment-gateway.yaml`. Do not hand-write these DTOs.

### 5.3 Implement Payment Validation (**FR-3** through **FR-8**)

Validate all fields per Section 2.1 rules. If any fail → return response with `REJECTED` status (**FR-1.3**), do NOT call bank, do NOT store.

Supported currencies: `GBP`, `USD`, `EUR` (**FR-6** — ISO 4217, max 3 per spec).

### 5.4 Integrate with Bank Simulator (**FR-11**)

Use the **generated bank client** (from `doc/openapi/bank-simulator.yaml`) with `RestClient` (Spring Framework 7 replacement for deprecated `RestTemplate`):

In `PaymentGatewayService`:
1. Inject the generated `PaymentsApi` bank client (backed by `RestClient` with configurable timeouts)
2. Map validated payment data → generated `BankPaymentRequest` model
3. Call `bankClient.authorizePayment(request)` — URL externalized to `application.properties`
4. Map response: `authorized=true` → `AUTHORIZED` (**FR-1.1**), `false` → `DECLINED` (**FR-1.2**)
5. Handle 503 → `BankCommunicationException` → 502 response (**NFR-15**)
6. Store payment in repository (**FR-12**)
7. Return response with last 4 card digits only (**FR-10**)

**`RestClient` bean configuration** (replaces `RestTemplate` in `ApplicationConfiguration.java`):
```java
@Bean
public RestClient bankRestClient(@Value("${bank.simulator.url}") String baseUrl) {
    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(new ClientHttpRequestFactoryBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(10))
            .build())
        .build();
}
```

### 5.5 Response DTOs (**FR-9**, **FR-10**)

Response DTOs are now **generated** from `doc/openapi/payment-gateway.yaml`:
- `ProcessPaymentResponse` — for POST (with `cardNumberLastFour` as `String`, PCI-safe)
- `PaymentDetailsResponse` — for GET (same fields, status only `Authorized`/`Declined`)
- `ErrorResponse` — for error responses

The old hand-written `PostPaymentResponse`, `GetPaymentResponse`, `ErrorResponse` are **deleted** and replaced by generated code. All field types, naming, and JSON serialization are driven by the OpenAPI spec.

### 5.6 Fix Repository Thread Safety (**NFR-4**)

- Replace `HashMap` with `ConcurrentHashMap` — **NFR-4**

### 5.7 Enhance Exception Handling (**NFR-15**)

- Add handler for validation errors → 400 (**FR-1.3**)
- Add handler for bank communication errors → 502 (**NFR-15**)
- Add generic handler → 500 (no stack trace to client)

### 5.8 Tests for Functional Requirements (**NFR-14**)

**File**: `src/test/java/.../controller/PaymentProcessingTest.java`

| # | Test | Expected | Covers |
|---|---|---|---|
| 1 | POST valid card (odd last digit) | 200, `Authorized`, has `id` | **FR-1.1** |
| 2 | POST valid card (even last digit) | 200, `Declined` | **FR-1.2** |
| 3 | POST card number too short (13 chars) | 400, `Rejected` | **FR-3** |
| 4 | POST card number too long (20 chars) | 400, `Rejected` | **FR-3** |
| 5 | POST card number non-numeric | 400, `Rejected` | **FR-3** |
| 6 | POST expired card (past month/year) | 400, `Rejected` | **FR-5** |
| 7 | POST invalid currency ("XYZ") | 400, `Rejected` | **FR-6** |
| 8 | POST invalid CVV (2 digits) | 400, `Rejected` | **FR-8** |
| 9 | POST missing fields | 400, `Rejected` | **FR-3..FR-8** |
| 10 | POST → GET round-trip | POST returns ID, GET by ID returns same payment | **FR-1**, **FR-2** |
| 11 | Response only has last 4 card digits | `cardNumberLastFour` is last 4 of submitted card | **FR-10** |

### Verification

```bash
docker-compose up -d           # Start bank simulator
./gradlew test                 # All tests pass
# Manual smoke test:
curl -X POST http://localhost:8090/payment \
  -H "Content-Type: application/json" \
  -d '{"card_number":"2222405343248877","expiry_month":12,"expiry_year":2030,"currency":"GBP","amount":100,"cvv":"123"}'
```

---

# 6. MVP — Non-Functional Requirements

> **STATUS: PARTIALLY COMPLETED** — Sprint 3, PAYGATE-12 through PAYGATE-19 (`b8b4191`..`5dd001d`). Sections 6.7, 6.10, 6.12, 6.13 deferred to Sprint 4.

**Goal**: Production-grade non-functional requirements: observability, security, resilience, performance validation.

### 6.1 Observability & Logging (**NFR-5**)

> **STATUS: COMPLETED** — Sprint 3, PAYGATE-12 (`b8b4191`)

**Dependencies to add**:
```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

**Configuration**: `src/main/resources/logback-spring.xml`

- JSON format for production (`-Dspring.profiles.active=prod`)
- Human-readable for development (default)
- MDC fields: `correlationId`, `paymentId`, `merchantId`
- **PCI masking** (**NFR-3**): Pattern replacement for card number patterns (`\d{14,19}` → `****XXXX`)
- Log levels: `INFO` for business events, `DEBUG` for flow, `ERROR` for exceptions

**Logging points**:

| Location | Level | What | Covers |
|---|---|---|---|
| Controller entry | INFO | `Payment processing requested` with correlationId | **NFR-5** |
| Validation failure | WARN | `Payment rejected: {reason}` — never log card data | **NFR-5**, **NFR-1** |
| Bank call start | DEBUG | `Calling bank simulator` with correlationId | **NFR-5** |
| Bank response | INFO | `Bank responded: authorized={bool}` | **NFR-5** |
| Bank error | ERROR | `Bank communication failure: {status}` | **NFR-5**, **NFR-15** |
| Payment stored | INFO | `Payment {id} stored with status {status}` | **NFR-5** |
| Payment retrieved | DEBUG | `Payment {id} retrieved` | **NFR-5** |
| Payment not found | WARN | `Payment {id} not found` | **NFR-5** |

### 6.2 Metrics & Grafana Dashboards (**NFR-6**, **NFR-7**)

> **STATUS: COMPLETED** — Sprint 3, PAYGATE-13 (`6761f2b`)

**Dependencies to add**:
```gradle
implementation 'io.micrometer:micrometer-registry-prometheus'
```

**Configuration** in `application.properties`:
```properties
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.endpoint.prometheus.enabled=true
management.metrics.tags.application=payment-gateway
```

**Custom Metrics** (**NFR-6**):

| Metric Name | Type | Labels | Covers |
|---|---|---|---|
| `payment.process.total` | Counter | `status={authorized,declined,rejected}` | **NFR-6**, **NFR-13** |
| `payment.process.duration` | Timer | `status` | **NFR-6**, **NFR-13** |
| `payment.retrieve.total` | Counter | `found={true,false}` | **NFR-6** |
| `bank.call.total` | Counter | `result={success,error}` | **NFR-6** |
| `bank.call.duration` | Timer | `result` | **NFR-6**, **NFR-13** |

**Environment Metrics** (built-in via Actuator): JVM memory, threads, GC pauses, CPU, HTTP server requests.

**Grafana Dashboards** (**NFR-7**) — files in `doc/grafana/`:

**Dashboard 1: Environment/Technical Metrics**
- JVM Memory (heap used vs max), GC pause rate/duration, thread count, CPU usage
- HTTP request rate (by status code), response time (p50, p95, p99), active connections

**Dashboard 2: Business Metrics**
- Payment processing rate (per minute), success rate (authorized / total)
- Payment outcomes pie chart (authorized vs declined vs rejected)
- Bank call success rate, bank response time trend
- Currency distribution, average payment amount

### 6.3 Distributed Tracing (**NFR-8**)

> **STATUS: COMPLETED** — Sprint 3, PAYGATE-13 (`6761f2b`)

**Dependencies to add** (Spring Boot 4 provides a dedicated starter):
```gradle
implementation 'org.springframework.boot:spring-boot-starter-opentelemetry'
```
This new Boot 4 starter auto-configures the OpenTelemetry SDK and OTLP export for both metrics and traces. Replaces the manual `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` setup required in Boot 3.

**Configuration** in `application.properties`:
```properties
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
```

**Trace spans**:
- `POST /payment` (root span)
  - `validate-payment` (child span)
  - `bank-authorize` (child span — HTTP call to bank simulator)
  - `save-payment` (child span)
- `GET /payment/{id}` (root span)
  - `find-payment` (child span)

Correlation ID propagated via `X-Correlation-Id` header and MDC.

### 6.4 Alerting (**NFR-9**)

> **STATUS: COMPLETED** — Sprint 3, PAYGATE-14 (`4e7ce31`)

**File**: `doc/prometheus/alert-rules.yml`

```yaml
groups:
  - name: payment-gateway
    rules:
      - alert: HighBankErrorRate
        expr: rate(bank_call_total{result="error"}[5m]) / rate(bank_call_total[5m]) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Bank error rate exceeds 5% — NFR-9, NFR-15"

      - alert: HighRejectionRate
        expr: rate(payment_process_total{status="rejected"}[5m]) / rate(payment_process_total[5m]) > 0.3
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Payment rejection rate exceeds 30% — NFR-9"

      - alert: HighLatency
        expr: histogram_quantile(0.99, rate(payment_process_duration_seconds_bucket[5m])) > 5
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "p99 latency exceeds 5s — NFR-9, NFR-13"

      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m]) > 0.01
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "HTTP 5xx rate exceeds 1% — NFR-9, NFR-13"

      - alert: HighHeapUsage
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JVM heap usage exceeds 85% — NFR-9"
```

### 6.5 Security — PCI DSS (**NFR-1**, **NFR-2**, **NFR-3**)

> **STATUS: COMPLETED** — Sprint 3, PAYGATE-15 (`e29f653`)

| PCI DSS Requirement | Implementation | Covers |
|---|---|---|
| **Req 3.4**: Render PAN unreadable anywhere stored | Only `cardNumberLastFour` stored | **NFR-1**, **FR-10** |
| **Req 3.2**: Do not store CVV after authorization | CVV used transiently, never persisted | **NFR-2** |
| **Req 3.4**: Mask PAN in logs/displays | `toString()` masks card number, Logback pattern masking | **NFR-3** |
| **Req 6.5**: Address common coding vulnerabilities | Input validation (**FR-3..FR-8**), `@JsonIgnoreProperties(ignoreUnknown=true)` | **NFR-1** |
| **Req 10.1**: Audit trails | Structured logging with correlationId (**NFR-5**) | **NFR-5** |

### 6.6 Resilience — Circuit Breaker & Rate Limiting (**NFR-11**, **NFR-12**)

> **STATUS: COMPLETED** — Sprint 3, PAYGATE-16 (`8e995a7`)

**Dependencies to add**:
```gradle
implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
implementation 'com.bucket4j:bucket4j-core:8.10.1'
```

**Circuit Breaker** (**NFR-11**) — on `BankClientAdapter`:

Configuration per SLA defined in Section 3.3:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      bankClient:
        failureRateThreshold: 50
        slowCallDurationThreshold: 3s
        slowCallRateThreshold: 80
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
        slidingWindowSize: 10
```

Behavior:
- Circuit CLOSED: all calls pass through to bank
- Circuit OPEN: fail-fast with 502 → merchant gets immediate error instead of waiting for timeout
- Circuit HALF-OPEN: probe with 3 calls to check if bank recovered

**Rate Limiting** (**NFR-12**) — on controller endpoints:

Configuration per SLA defined in Section 3.3:
```java
// POST /payment: 100 req/s sustained, burst to 200
Bandwidth.classic(200, Refill.greedy(100, Duration.ofSeconds(1)))

// GET /payment/{id}: 500 req/s sustained, burst to 1000
Bandwidth.classic(1000, Refill.greedy(500, Duration.ofSeconds(1)))
```

Response when rate limited: `429 Too Many Requests` with `Retry-After` header.

### 6.7 Performance Testing (**NFR-13**)

> **STATUS: NOT STARTED** — Deferred to Sprint 4 (PAYGATE-23)

**Test environment**: Docker Compose defined in Section 3.3.

**Test tool**: k6 (lightweight, scriptable, Prometheus-compatible).

**File**: `perf/` directory

**Test scenarios**:

| # | Scenario | Duration | Load Profile | Pass Criteria | Validates |
|---|---|---|---|---|---|
| 1 | Smoke test | 30s | 1 VU, 1 req/s | No errors, p99 < 1s | Basic functionality under load |
| 2 | Load test | 5m | Ramp 1→100 VUs | p50 < 200ms, p99 < 1s, error rate < 0.1% | **NFR-13** SLA targets |
| 3 | Stress test | 5m | Ramp 1→500 VUs | Find saturation point (p99 crosses 1s) | Throughput ceiling |
| 4 | Soak test | 1h | 50 VUs constant | No memory leak, stable GC, p99 < 1s | Memory stability |
| 5 | Circuit breaker test | 3m | 50 VUs, bank fails at 1m | Circuit opens within 10s of bank failure, closes within 60s of recovery | **NFR-11** |
| 6 | Rate limit test | 1m | 200 VUs burst | 429 responses appear, legitimate traffic still served | **NFR-12** |

**k6 script skeleton** (`perf/load-test.js`):
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '3m', target: 100 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    'http_req_duration{endpoint:post_payment}': ['p(50)<200', 'p(99)<1000'],
    'http_req_duration{endpoint:get_payment}': ['p(50)<10', 'p(99)<50'],
    'http_req_failed': ['rate<0.001'],
  },
};

export default function () {
  // POST /payment with random valid card
  const postRes = http.post('http://localhost:8090/payment', JSON.stringify({
    card_number: '2222405343248877',
    expiry_month: 12,
    expiry_year: 2030,
    currency: 'GBP',
    amount: 100,
    cvv: '123',
  }), { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'post_payment' } });

  check(postRes, { 'POST status is 200': (r) => r.status === 200 });

  // GET /payment/{id}
  if (postRes.status === 200) {
    const paymentId = postRes.json('id');
    const getRes = http.get(`http://localhost:8090/payment/${paymentId}`,
      { tags: { endpoint: 'get_payment' } });
    check(getRes, { 'GET status is 200': (r) => r.status === 200 });
  }

  sleep(0.1);
}
```

**Deliverables** (produced from reference environment in Section 3.3):

| Deliverable | Format | Purpose |
|---|---|---|
| `perf/results/load-test-report.html` | k6 HTML report | Latency distribution, throughput, error rate |
| `perf/results/stress-test-saturation.md` | Markdown | Max sustained req/s before SLA breach |
| Grafana screenshot: load test | PNG | Visual proof of metrics during load |
| Grafana screenshot: circuit breaker | PNG | Circuit state transitions during bank failure |

### Verification

```bash
# Start full stack
docker-compose -f docker-compose.perf.yml up -d

# Run performance tests
k6 run perf/load-test.js
k6 run perf/stress-test.js
k6 run perf/circuit-breaker-test.js

# Check observability
curl http://localhost:8090/actuator/prometheus | grep payment
curl http://localhost:8090/actuator/health
# Open Grafana at http://localhost:3000
```

### 6.8 Code Quality — OWASP & SonarQube (**NFR-16**, **NFR-17**)

> **STATUS: COMPLETED** — Sprint 3, PAYGATE-18 (`496ede8`)

#### OWASP Dependency-Check (**NFR-16**)

**Plugin**: `org.owasp.dependencycheck` (12.1.1)

Scans all project dependencies (including transitive) against the NIST National Vulnerability Database (NVD) for known CVEs. Aligns with OWASP Top 10 A06:2021 — Vulnerable and Outdated Components.

**Configuration** in `build.gradle`:
```groovy
dependencyCheck {
    failBuildOnCVSS = 7.0f          // Fail on HIGH+ severity
    formats = ['HTML', 'JSON']       // Reports in build/reports/dependency-check/
    analyzers {
        assemblyEnabled = false      // Not a .NET project
        nodeEnabled = false          // Not a Node project
    }
    nvd {
        apiKey = findProperty('nvdApiKey') ?: ''  // NVD API key for faster downloads
    }
    suppressionFile = 'config/owasp-suppressions.xml'  // False positive suppressions
}
```

**Usage**:
```bash
./gradlew dependencyCheckAnalyze    # Run scan, report in build/reports/dependency-check/
./gradlew build                     # Fails if any dependency has CVSS >= 7
```

**Suppression file** (`config/owasp-suppressions.xml`): Used to suppress false positives after manual review. Each suppression must include a justification comment.

**CI integration**: Run `dependencyCheckAnalyze` as part of the build pipeline. Block merge if CVSS >= 7.

#### SonarQube (**NFR-17**)

**Plugin**: `org.sonarqube` (6.0.1.5171)

Static code analysis for bugs, code smells, security vulnerabilities, and test coverage tracking.

**Configuration** in `build.gradle`:
```groovy
sonar {
    properties {
        property 'sonar.projectKey', 'checkout-payment-gateway'
        property 'sonar.projectName', 'Payment Gateway'
        property 'sonar.sources', 'src/main/java'
        property 'sonar.tests', 'src/test/java'
        property 'sonar.java.coveragePlugin', 'jacoco'
        property 'sonar.coverage.jacoco.xmlReportPaths',
            "${buildDir}/reports/jacoco/test/jacocoTestReport.xml"
    }
}
```

**JaCoCo** (for coverage reporting to SonarQube) — add to `build.gradle`:
```groovy
plugins {
    id 'jacoco'
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true     // Required by SonarQube
        html.required = true    // Human-readable
    }
}
```

**Quality Gate** (configured in SonarQube server):

| Metric | Threshold | Scope |
|---|---|---|
| New bugs | 0 | New code only |
| New vulnerabilities | 0 | New code only |
| New security hotspots reviewed | 100% | New code only |
| New code coverage | ≥ 80% | New code only |
| New code duplications | ≤ 3% | New code only |

**Usage**:
```bash
./gradlew test jacocoTestReport sonar    # Run tests, generate coverage, push to SonarQube
```

**CI integration**: Run after tests in the build pipeline. SonarQube quality gate must pass before merge.

**Note**: SonarQube server is an external dependency (self-hosted or SonarCloud). For local development, use `sonar.host.url=http://localhost:9000`. For CI, configure via environment variables (`SONAR_HOST_URL`, `SONAR_TOKEN`).

### 6.9 Environment Configuration — .env + spring-dotenv (**NFR-18**)

> **STATUS: COMPLETED** — Sprint 3, PAYGATE-17 (`fd461c0`)

Externalize all environment-specific config via `.env` files so secrets and per-environment values stay out of source control.

**Dependency** — add to `build.gradle`:
```groovy
implementation 'me.paulschwarz:spring-dotenv:4.0.0'
```

spring-dotenv auto-loads `.env` from the project root and makes values available as `${env.VAR_NAME}` placeholders in `application.yml`. Zero code changes required — Spring's `PropertySource` resolution handles it.

**Files to create**:

| File | Purpose |
|---|---|
| `.env.example` | Documented template with all env vars and safe defaults — committed to repo |

```dotenv
# Server configuration
SERVER_PORT=8090

# Bank simulator connection
BANK_SIMULATOR_URL=http://localhost:8080
BANK_SIMULATOR_CONNECT_TIMEOUT=10s
BANK_SIMULATOR_READ_TIMEOUT=10s

# Swagger UI (set to false in production)
SPRINGDOC_SWAGGER_ENABLED=true
SPRINGDOC_API_DOCS_ENABLED=true
```

**Files to modify**:

| File | Change |
|---|---|
| `.gitignore` | Add `.env` entry to prevent secrets from being committed |
| `src/main/resources/application.yml` | Replace hardcoded values with `${ENV_VAR:default}` placeholders |

Updated `application.yml`:
```yaml
server:
  port: ${SERVER_PORT:8090}

springdoc:
  swagger-ui:
    enabled: ${SPRINGDOC_SWAGGER_ENABLED:true}
  api-docs:
    enabled: ${SPRINGDOC_API_DOCS_ENABLED:true}

bank:
  simulator:
    url: ${BANK_SIMULATOR_URL:http://localhost:8080}
    connect-timeout: ${BANK_SIMULATOR_CONNECT_TIMEOUT:10s}
    read-timeout: ${BANK_SIMULATOR_READ_TIMEOUT:10s}
```

**Key design decision**: Every placeholder has a `:default` value matching current hardcoded values. The app works identically without any `.env` file, preserving backward compatibility. Existing tests pass since they never depend on a `.env` file.

**Verification**:
```bash
./gradlew test                     # All tests pass without .env file
cp .env.example .env && ./gradlew bootRun  # Verify override works
```

### 6.10 CI/CD Pipeline — GitHub Actions (**NFR-19**)

> **STATUS: NOT STARTED** — Deferred to Sprint 4 (PAYGATE-20)

Free CI/CD using GitHub Actions (2000 min/month free tier). Triggered on every push and PR to `main`.

**File to create**: `.github/workflows/ci.yml`

**Architecture**: 5 jobs, each a distinct GitHub status check (for branch protection in 6.12):

| Job Name | Gradle Task | Depends On | Purpose |
|---|---|---|---|
| `build-and-test` | `./gradlew build` | — | Compile + all unit/integration tests |
| `checkstyle` | `./gradlew checkstyleMain checkstyleTest` | build-and-test | Code style enforcement |
| `coverage` | `./gradlew test jacocoTestReport jacocoTestCoverageVerification` | build-and-test | JaCoCo ≥80% line coverage |
| `spotbugs` | `./gradlew spotbugsMain` | build-and-test | Static analysis + FindSecBugs security |
| `owasp` | `./gradlew dependencyCheckAnalyze` | build-and-test | Dependency CVE scan |

All jobs share: checkout → setup JDK 17 (temurin) → cache Gradle → run task → upload report artifact.

**Design decisions**:
- **Separate jobs per gate**: Each shows as a distinct status check in GitHub. Failure in one doesn't block visibility of others. Quality gates run in parallel after build succeeds.
- **No docker-compose needed**: All existing tests mock the bank via `@MockBean` / Mockito. No test calls the bank simulator endpoint.
- **NVD database cached**: OWASP dependency-check downloads the NVD database (~2GB first run). Cached between CI runs.
- **Coverage badge**: Generated via `cicirello/jacoco-badge-generator` on main branch pushes.

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

permissions:
  contents: read
  checks: write

jobs:
  build-and-test:
    name: Build & Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
      - run: chmod +x gradlew && ./gradlew build
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: test-results, path: build/reports/tests/ }

  checkstyle:
    name: Checkstyle
    runs-on: ubuntu-latest
    needs: build-and-test
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
      - run: ./gradlew checkstyleMain checkstyleTest
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: checkstyle-report, path: build/reports/checkstyle/ }

  coverage:
    name: Code Coverage
    runs-on: ubuntu-latest
    needs: build-and-test
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
      - run: ./gradlew test jacocoTestReport jacocoTestCoverageVerification
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: coverage-report, path: build/reports/jacoco/ }
      - uses: cicirello/jacoco-badge-generator@v2
        if: github.ref == 'refs/heads/main'
        with:
          jacoco-csv-file: build/reports/jacoco/test/jacocoTestReport.csv
          badges-directory: .github/badges
          generate-coverage-badge: true

  spotbugs:
    name: SpotBugs
    runs-on: ubuntu-latest
    needs: build-and-test
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
      - run: ./gradlew spotbugsMain
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: spotbugs-report, path: build/reports/spotbugs/ }

  owasp:
    name: OWASP Dependency Check
    runs-on: ubuntu-latest
    needs: build-and-test
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
      - uses: actions/cache@v4
        with:
          path: ~/.gradle/dependency-check-data
          key: ${{ runner.os }}-nvd-${{ github.run_id }}
          restore-keys: ${{ runner.os }}-nvd-
      - run: ./gradlew dependencyCheckAnalyze
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: owasp-report, path: build/reports/ }
```

### 6.11 Quality Gates — Checkstyle, JaCoCo, SpotBugs+FindSecBugs (**NFR-20**)

> **STATUS: COMPLETED** — Sprint 3, PAYGATE-18 (`496ede8`)

All plugins configured to **exclude generated code** (`build/generated/`). Wired into Gradle `check` lifecycle (except OWASP — too slow for local dev).

#### 6.11.1 Checkstyle

**Plugin**: built-in `checkstyle` (toolVersion `10.12.5`)

```groovy
// build.gradle
plugins {
    id 'checkstyle'
}

checkstyle {
    toolVersion = '10.12.5'
    configFile = file("${rootDir}/config/checkstyle/checkstyle.xml")
    maxWarnings = 0
    maxErrors = 0
}

tasks.withType(Checkstyle).configureEach {
    exclude '**/generated/**'
    source = fileTree('src/main/java')
}
checkstyleTest {
    source = fileTree('src/test/java')
}
```

**File to create**: `config/checkstyle/checkstyle.xml`

Google Java Style base, customized to match `.editorconfig`:
- 2-space indentation, 4-space continuation
- 100-char line limit
- No star imports, no unused imports
- Braces required, standard whitespace rules
- Naming conventions (camelCase fields, PascalCase types, UPPER_SNAKE constants)
- Generated code excluded via `SuppressionXpathSingleFilter`

#### 6.11.2 JaCoCo (Code Coverage)

**Plugin**: built-in `jacoco` (toolVersion `0.8.11`)

```groovy
// build.gradle
plugins {
    id 'jacoco'
}

jacoco {
    toolVersion = '0.8.11'
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true   // CI badge generation + SonarQube
        html.required = true  // Local developer feedback
    }
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.collect {
            fileTree(dir: it, exclude: [
                '**/generated/**',
                '**/bank/api/**', '**/bank/model/**',
                '**/api/model/**',
            ])
        }))
    }
}

jacocoTestCoverageVerification {
    dependsOn jacocoTestReport
    violationRules {
        rule {
            limit {
                minimum = 0.80  // 80% line coverage minimum
            }
        }
    }
    afterEvaluate {
        classDirectories.setFrom(jacocoTestReport.classDirectories)
    }
}

check.dependsOn jacocoTestCoverageVerification
```

**Coverage justification**: 80% is achievable — the project has 9 test classes covering every hand-written source class (controller, service, repository, mappers, validator, adapter, exception handler). Generated code is excluded.

#### 6.11.3 SpotBugs + FindSecBugs

**Plugin**: `com.github.spotbugs` version `5.2.3` (SpotBugs `4.8.3`)

```groovy
// build.gradle
plugins {
    id 'com.github.spotbugs' version '5.2.3'
}

spotbugs {
    toolVersion = '4.8.3'
    effort = 'max'
    reportLevel = 'medium'
    excludeFilter = file("${rootDir}/config/spotbugs/exclude.xml")
}

dependencies {
    spotbugsPlugins 'com.h3xstream.findsecbugs:findsecbugs-plugin:1.12.0'
}

tasks.withType(com.github.spotbugs.snom.SpotBugsTask).configureEach {
    reports {
        html.required = true
        xml.required = true
    }
    classes = files(classes.filter { !it.path.contains('/generated/') })
}
```

**File to create**: `config/spotbugs/exclude.xml` — exclude generated packages (`client.bank.*`, `api.*`) and MapStruct implementations.

**Why FindSecBugs for payment domain**: Detects logging sensitive data (card numbers in log statements), insecure cryptographic operations, SQL injection (future-proofing), XSS vulnerabilities, hardcoded credentials — all critical for PCI DSS compliance (**NFR-1**, **NFR-2**, **NFR-3**).

#### 6.11.4 OWASP Dependency-Check (CI-only)

Extends existing Section 6.8 config. **Not wired into `check`** — runs as a separate CI job via `./gradlew dependencyCheckAnalyze` because the first run downloads the NVD database (~2GB), making it too slow for the local dev loop.

**Verification** (all quality gates):
```bash
./gradlew check                       # build + tests + checkstyle + jacoco + spotbugs
./gradlew dependencyCheckAnalyze      # OWASP (separate, slow)
```

**Note**: After adding Checkstyle/SpotBugs, run them first and fix any violations in existing source code before proceeding to CI.

### 6.12 Branch Protection — GitHub Configuration (**NFR-21**)

> **STATUS: NOT STARTED** — Deferred to Sprint 4 (PAYGATE-21)

**File to create**: `doc/branch-protection.md`

Document GitHub repository settings to configure manually (requires admin access):

#### Settings: Repository → Settings → Branches → Add rule for `main`

| Setting | Value | Rationale |
|---|---|---|
| **Require a pull request before merging** | Yes | No direct commits to main |
| Required approving reviews | 1 | Code review before merge |
| Dismiss stale PR approvals on new commits | Yes | Re-review after changes |
| **Require status checks to pass** | Yes | CI must be green |
| Required checks | `Build & Test`, `Checkstyle`, `Code Coverage`, `SpotBugs`, `OWASP Dependency Check` | All 5 CI jobs from Section 6.10 |
| Require branches to be up to date | Yes | No stale merges |
| **Do not allow bypassing the above settings** | Recommended | Even admins follow the rules |

#### CI Status Checks Mapping

| GitHub Status Check | CI Job Name | What It Enforces |
|---|---|---|
| `Build & Test` | `build-and-test` | Compilation + all unit/integration tests |
| `Checkstyle` | `checkstyle` | Code style conformance (**NFR-20**) |
| `Code Coverage` | `coverage` | JaCoCo ≥ 80% line coverage (**NFR-20**) |
| `SpotBugs` | `spotbugs` | Static analysis + FindSecBugs security (**NFR-20**) |
| `OWASP Dependency Check` | `owasp` | No high/critical CVEs in dependencies (**NFR-16**) |

### 6.13 GitHub Badges (**NFR-22**)

> **STATUS: NOT STARTED** — Deferred to Sprint 4 (PAYGATE-22)

**File to modify**: `README.md` — add badges section at top, before existing content.

```markdown
# Payment Gateway Challenge

![Build](https://github.com/<OWNER>/<REPO>/actions/workflows/ci.yml/badge.svg)
![Coverage](https://img.shields.io/badge/coverage-80%25-brightgreen)
![Checkstyle](https://img.shields.io/badge/checkstyle-passing-brightgreen)
![SpotBugs](https://img.shields.io/badge/spotbugs-passing-brightgreen)
![OWASP](https://img.shields.io/badge/OWASP-passing-brightgreen)
![Java](https://img.shields.io/badge/java-17-blue)
![Spring Boot](https://img.shields.io/badge/spring%20boot-3.1.5-green)
```

| Badge | Source | Dynamic? |
|---|---|---|
| **Build** | GitHub Actions native badge URL | Yes — updates on every CI run |
| **Coverage** | shields.io static (initially 80%) | No — make dynamic later via Codecov or committed SVG |
| **Checkstyle** | shields.io static | No — indicates checkstyle is enforced |
| **SpotBugs** | shields.io static | No — indicates SpotBugs+FindSecBugs is enforced |
| **OWASP** | shields.io static | No — indicates OWASP check is enforced |
| **Java 17** | shields.io static | No — tech stack indicator |
| **Spring Boot 3.1.5** | shields.io static | No — tech stack indicator |

**Note**: `<OWNER>/<REPO>` must be replaced with the actual GitHub repository path. The build badge is the only truly dynamic badge out of the box. Coverage can be made dynamic by integrating with Codecov or SonarCloud (extends **NFR-17**).

### 6.9–6.13 File Inventory

**Files to create** (6 files):

| File | Purpose | Section |
|---|---|---|
| `.env.example` | Env var template | 6.9 |
| `config/checkstyle/checkstyle.xml` | Checkstyle rules (Google style + .editorconfig) | 6.11.1 |
| `config/spotbugs/exclude.xml` | SpotBugs exclusion filter for generated code | 6.11.3 |
| `config/owasp/suppressions.xml` | OWASP false-positive suppression template | 6.11.4 |
| `.github/workflows/ci.yml` | CI/CD pipeline (5 jobs) | 6.10 |
| `doc/branch-protection.md` | Branch protection setup guide | 6.12 |

**Files to modify** (4 files):

| File | Change | Section |
|---|---|---|
| `build.gradle` | +4 plugins (checkstyle, jacoco, spotbugs, owasp), +spring-dotenv dep, +findsecbugs dep, all config blocks | 6.9, 6.11 |
| `.gitignore` | +`.env` entry | 6.9 |
| `src/main/resources/application.yml` | Env var placeholders with defaults | 6.9 |
| `README.md` | Badges section at top | 6.13 |

**Implementation order**:
```
6.9  Environment config (.env)     ─┐
                                    ├─→  6.10 CI/CD (GitHub Actions)  ─→  6.12 Branch Protection
6.11 Quality gates (Gradle plugins) ─┘                                ─→  6.13 Badges + README
```

Phases 6.9 and 6.11 are independent. Both must complete before 6.10 (CI needs quality gate tasks in build.gradle). 6.12 and 6.13 depend on 6.10 (reference CI job names).

**Verification**:
```bash
./gradlew check                       # build + tests + checkstyle + jacoco + spotbugs all green
./gradlew dependencyCheckAnalyze      # OWASP scan passes (or known CVEs suppressed)
./gradlew bootRun                     # App starts on port 8090
```

---

# 7. Post-MVP Scope

> **STATUS: NOT STARTED** — Deferred to Sprint 4 (PAYGATE-24 through PAYGATE-28)

## 7.1 Hexagonal Architecture Refactoring

> **STATUS: NOT STARTED** — Deferred to Sprint 4 (PAYGATE-24)

**Goal**: Restructure the working code into clean hexagonal architecture with CQRS, applying SOLID throughout.

### Target Package Structure

```
com.checkout.payment.gateway/
├── PaymentGatewayApplication.java
│
├── generated/                             ← AUTO-GENERATED (do not edit, not committed)
│   ├── api/
│   │   └── PaymentsApi.java              (server interface from payment-gateway.yaml)
│   ├── model/
│   │   ├── ProcessPaymentRequest.java    (from payment-gateway.yaml)
│   │   ├── ProcessPaymentResponse.java   (from payment-gateway.yaml)
│   │   ├── PaymentDetailsResponse.java   (from payment-gateway.yaml)
│   │   └── ErrorResponse.java            (from payment-gateway.yaml)
│   └── bank/
│       ├── api/
│       │   └── PaymentsApi.java          (client interface from bank-simulator.yaml)
│       └── model/
│           ├── BankPaymentRequest.java   (from bank-simulator.yaml)
│           ├── BankPaymentResponse.java  (from bank-simulator.yaml)
│           └── BankErrorResponse.java    (from bank-simulator.yaml)
│
├── domain/                                ← HAND-WRITTEN (spec-independent)
│   ├── model/
│   │   ├── Payment.java                  (entity)
│   │   ├── PaymentStatus.java            (enum)
│   │   ├── CardNumber.java               (value object, record)
│   │   ├── Cvv.java                      (value object, record)
│   │   ├── ExpiryDate.java               (value object, record)
│   │   ├── Currency.java                 (enum, ISO 4217)
│   │   └── Money.java                    (value object, record)
│   ├── port/
│   │   ├── in/
│   │   │   ├── ProcessPaymentUseCase.java
│   │   │   ├── ProcessPaymentCommand.java
│   │   │   ├── ProcessPaymentResult.java
│   │   │   ├── GetPaymentUseCase.java
│   │   │   └── PaymentDetails.java
│   │   └── out/
│   │       ├── PaymentRepository.java
│   │       └── BankClient.java           (domain port — NOT the generated client)
│   ├── service/
│   │   ├── ProcessPaymentService.java
│   │   └── GetPaymentService.java
│   └── exception/
│       ├── PaymentNotFoundException.java
│       └── PaymentValidationException.java
│
├── adapter/                               ← HAND-WRITTEN (maps generated ↔ domain)
│   ├── in/web/
│   │   ├── PaymentController.java        (implements generated PaymentsApi)
│   │   ├── GlobalExceptionHandler.java
│   │   └── PaymentRequestMapper.java     (generated DTO ↔ domain command/result)
│   └── out/
│       ├── bank/
│       │   └── BankClientAdapter.java    (wraps generated bank client, implements domain BankClient port)
│       └── persistence/
│           └── InMemoryPaymentRepository.java
│
└── configuration/
    ├── ApplicationConfiguration.java     (RestClient bean, not RestTemplate)
    └── BankClientProperties.java
```

### Dependency Rule

**`domain/` has ZERO imports from `adapter/`, `configuration/`, or Spring framework.**

```
adapter/in/web → depends on → domain/port/in (interfaces)
adapter/out/bank → implements → domain/port/out (BankClient)
adapter/out/persistence → implements → domain/port/out (PaymentRepository)
domain/service → depends on → domain/port/out (interfaces only)
configuration → wires everything together
```

### SOLID Principles Mapping

| Principle | Manifestation |
|---|---|
| **S — Single Responsibility** | Each use case class handles one operation. Each value object validates one concept |
| **O — Open/Closed** | New currency = add enum constant. New persistence = implement `PaymentRepository` |
| **L — Liskov Substitution** | `InMemoryPaymentRepository` is swappable with JPA/Redis impl |
| **I — Interface Segregation** | `ProcessPaymentUseCase` and `GetPaymentUseCase` are separate interfaces |
| **D — Dependency Inversion** | Domain defines `BankClient` and `PaymentRepository` interfaces. Infrastructure implements them |

### CQRS Split

**Command path** (write): `PaymentController` → `ProcessPaymentUseCase` → `ProcessPaymentService` → `BankClient` + `PaymentRepository`

**Query path** (read): `PaymentController` → `GetPaymentUseCase` → `GetPaymentService` → `PaymentRepository`

### Value Objects (Java 25 Records)

| Value Object | Validates | PCI Safety | Covers |
|---|---|---|---|
| `CardNumber(String value)` | 14-19 digits, numeric only | `toString()` → `"****XXXX"`, `lastFour()` | **FR-3**, **NFR-1** |
| `Cvv(String value)` | 3-4 digits, numeric only | `toString()` → `"***"` | **FR-8**, **NFR-2** |
| `ExpiryDate(int month, int year)` | Month 1-12, future date | `toBankFormat()` → `"MM/YYYY"` | **FR-4**, **FR-5** |
| `Currency` (enum) | `GBP`, `USD`, `EUR` | `fromString()` factory | **FR-6** |
| `Money(int amount, Currency currency)` | Positive amount | — | **FR-7** |

### Full Test Suite (**NFR-14**)

```
┌─────────────────────────────┐
│         E2E Tests           │  ← docker-compose up required
│    (1-2 full flow tests)    │
├─────────────────────────────┤
│      Contract Tests         │  ← docker-compose up required
│  (BankClientAdapter vs sim) │
├─────────────────────────────┤
│    Integration Tests        │  ← @SpringBootTest + MockMvc
│  (controller → service →    │     @MockBean for BankClient
│   repo, bank mocked)        │
├─────────────────────────────┤
│       Unit Tests            │  ← No Spring, pure JUnit + Mockito
│  (value objects, services,  │
│   mapper, repository)       │
└─────────────────────────────┘
```

**Unit tests** (~25 tests): CardNumber, Cvv, ExpiryDate, Currency, Money, ProcessPaymentService, GetPaymentService, PaymentRequestMapper

**Integration tests** (~10 tests): POST authorized/declined/rejected, GET found/not-found, round-trip

**Contract tests** (`@Tag("contract")`, ~4 tests): BankClientAdapter vs simulator (odd→authorized, even→declined, zero→error)

**E2E tests** (`@Tag("e2e")`, ~2 tests): Full POST→GET round-trip with Docker

### Refactoring Order

1. Create all port interfaces and domain model (new files, no behavior change)
2. Create adapter implementations (wrapping existing logic)
3. Create new controller delegating to use cases
4. Run all MVP tests — must still pass
5. Delete old files
6. Add full unit test suite for new structure
7. Run all tests

---

## 7.2 ISO 20022 Banking Contracts

> **STATUS: NOT STARTED** — Deferred to Sprint 4 (PAYGATE-25)

**Current state**: Bank simulator uses a simple proprietary JSON contract (`card_number`, `expiry_date`, etc.).

**ISO 20022** is the global standard for financial messaging (replacing ISO 8583). Key message types:
- `pacs.008` — FI to FI Customer Credit Transfer
- `pain.001` — Customer Credit Transfer Initiation
- `camt.053` — Bank to Customer Statement

**Impact on architecture**: Current bank adapter is a port — swappable without domain changes. Production bank adapter would marshal ISO 20022 XML messages. Hexagonal architecture makes this a new adapter implementation, zero domain changes.

**Implementation**: ISO 4217 currency validation is already in `Currency` enum (**FR-6**).

---

## 7.3 mTLS for Bank Communication

> **STATUS: NOT STARTED** — Deferred to Sprint 4 (PAYGATE-26)

**Current**: Plain HTTP to `localhost:8080` (simulator).

**Production mTLS configuration** (documented, not implemented):

```yaml
# application-prod.yml
bank:
  simulator:
    url: https://bank.acquiring.internal/payments
  tls:
    key-store: classpath:client-keystore.p12
    key-store-password: ${BANK_KEYSTORE_PASS}
    trust-store: classpath:bank-truststore.p12
    trust-store-password: ${BANK_TRUSTSTORE_PASS}
```

Implementation path:
- `SSLContext` / `SSLBundle` from keystore/truststore (Spring Boot 4 `SslBundles` auto-configuration)
- `RestClient.Builder` with custom `ClientHttpRequestFactory` using `SSLBundle`
- `@Profile("prod")` bean overriding default `RestClient`
- Certificate rotation via Spring Cloud Vault or AWS Secrets Manager

---

## 7.4 Additional Production Improvements

> **STATUS: NOT STARTED** — Deferred to Sprint 4 (PAYGATE-27, PAYGATE-28)

| Improvement | Pattern | Complexity | Value | Enables |
|---|---|---|---|---|
| **Idempotency** | `Idempotency-Key` header + cache | Medium | Prevents double-charging | **FR-1** safety |
| **Database** | JPA adapter for PaymentRepository | Low | Durable storage (1 new adapter, zero domain changes) | **FR-12** durability |
| **Merchant auth** | API key authentication | Medium | Identifies merchants | **NFR-12** per-merchant rate limiting |
| **`@HttpServiceClient`** | Boot 4 declarative HTTP client for bank adapter | Low | Replaces manual RestClient wiring with annotated interface | **FR-11** cleaner bank integration |
| **Event Sourcing** | Payment state changes as domain events | High | Full audit trail, replay | PCI DSS Req 10.1 |
| **API Versioning** | Boot 4 built-in API versioning (auto-configured) | Low | Backward compatibility | — |
| **API Gateway** | Spring Cloud Gateway | High | Auth, rate limiting, routing | **NFR-12** centralized |
| **Async Processing** | Message queue for bank calls | High | Higher throughput under load | **NFR-13** scalability |

---

## Summary: Implementation Sequence

**Planned sequence** (original):
```
Section 4: Tests for existing code          ./gradlew test (pass on existing code)
            ↓
Section 3.4: Toolchain migration            ./gradlew clean test (pass on new versions)
            ↓
Section 5: MVP FR implementation            ./gradlew test + manual smoke test
            ↓
Section 6.1-6.7: Observability, security,   ./gradlew bootRun + curl /actuator/prometheus
  resilience, perf testing                   k6 run perf/*.js + Grafana dashboards
            ↓
Section 6.8: OWASP + SonarQube             ./gradlew dependencyCheckAnalyze sonar
            ↓
Section 6.9: Env config (.env)              ./gradlew test (no .env, defaults work)
Section 6.11: Quality gates (Gradle)        ./gradlew check (checkstyle + jacoco + spotbugs)
            ↓
Section 6.10: CI/CD (GitHub Actions)        Push workflow, verify green on GitHub
            ↓
Section 6.12: Branch protection             Configure GitHub settings manually
Section 6.13: Badges                        Update README.md with badge URLs
            ↓
Section 7.1: Hexagonal refactoring          ./gradlew test (full suite)
            ↓
Section 7.2-7.4: Future improvements        Documentation + design review
```

**Actual sequence** (what was implemented, with sprint/PAYGATE mapping):

| Planned Section | Actual Sprint | PAYGATE | Status |
|----------------|---------------|---------|--------|
| Section 4: Tests for existing code | Sprint 2 | PAYGATE-2 | COMPLETED |
| Section 3.4: Toolchain migration | — | — | SKIPPED (stayed on Java 17 / Boot 3.1.5) |
| Section 5: MVP FR implementation | Sprint 2 | PAYGATE-3 through PAYGATE-11 | COMPLETED |
| Section 6.1: Observability & Logging | Sprint 3 | PAYGATE-12 | COMPLETED |
| Section 6.2: Metrics & Dashboards | Sprint 3 | PAYGATE-13 | COMPLETED |
| Section 6.3: Distributed Tracing | Sprint 3 | PAYGATE-13 | COMPLETED |
| Section 6.4: Alerting | Sprint 3 | PAYGATE-14 | COMPLETED |
| Section 6.5: PCI DSS | Sprint 3 | PAYGATE-15 | COMPLETED |
| Section 6.6: Resilience | Sprint 3 | PAYGATE-16 | COMPLETED |
| Section 6.7: Performance Testing | Sprint 4 | PAYGATE-23 | NOT STARTED |
| Section 6.8: OWASP & SonarQube | Sprint 3 | PAYGATE-18 | COMPLETED |
| Section 6.9: Env config | Sprint 3 | PAYGATE-17 | COMPLETED |
| Section 6.11: Quality gates | Sprint 3 | PAYGATE-18 | COMPLETED |
| Section 6.10: CI/CD | Sprint 4 | PAYGATE-20 | NOT STARTED |
| Section 6.12: Branch protection | Sprint 4 | PAYGATE-21 | NOT STARTED |
| Section 6.13: Badges | Sprint 4 | PAYGATE-22 | NOT STARTED |
| Section 7.1: Hexagonal refactoring | Sprint 4 | PAYGATE-24 | NOT STARTED |
| Section 7.2-7.4: Future improvements | Sprint 4 | PAYGATE-25 through PAYGATE-28 | NOT STARTED |

---

# Appendix A: C4 Architecture Diagrams

All diagrams are in PlantUML format in `doc/diagram/`. Render with any PlantUML-compatible tool (IDE plugin, `plantuml.jar`, or online at plantuml.com).

| Diagram | File | Description |
|---|---|---|
| Level 1: Context | [`c4-level1-context.puml`](diagram/c4-level1-context.puml) | System context — Merchant, Payment Gateway, Acquiring Bank |
| Level 2: Container | [`c4-level2-container.puml`](diagram/c4-level2-container.puml) | Containers — Spring Boot 4 app (Java 25), in-memory store, bank simulator, observability stack |
| Level 3: Component | [`c4-level3-component.puml`](diagram/c4-level3-component.puml) | Components — Hexagonal architecture with generated API + bank client (Post-MVP target) |
| Level 4: Sequence | [`c4-level4-sequence.puml`](diagram/c4-level4-sequence.puml) | Payment processing flow — validation, bank call, all three outcomes (Authorized/Declined/Rejected) |

To render all diagrams:
```bash
# Using plantuml.jar
java -jar plantuml.jar doc/diagram/*.puml

# Using Docker
docker run -v $(pwd)/doc/diagram:/data plantuml/plantuml /data/*.puml
```

---

# Appendix B: 3rd Party Contracts (OpenAPI)

All contracts are defined as OpenAPI 3.1 specifications in `doc/openapi/`. Models and API interfaces are **generated** from these specs using the `openapi-generator-gradle-plugin` (7.19.0).

### OpenAPI Spec Files

| Spec | File | Generator | Purpose |
|---|---|---|---|
| Bank Simulator | [`doc/openapi/bank-simulator.yaml`](openapi/bank-simulator.yaml) | `java` (library: `restclient`) | Client models + API for calling the bank |
| Payment Gateway | [`doc/openapi/payment-gateway.yaml`](openapi/payment-gateway.yaml) | `spring` (interfaceOnly: `true`) | Server interfaces + DTOs for our REST API |

### Bank Simulator Contract Summary

Full spec: `doc/openapi/bank-simulator.yaml`

**Endpoint**: `POST http://localhost:8080/payments`

**Schemas** (→ generated as Java classes):
- `BankPaymentRequest` — `card_number`, `expiry_date` (MM/YYYY), `currency`, `amount`, `cvv`
- `BankPaymentResponse` — `authorized` (boolean), `authorization_code` (string)
- `BankErrorResponse` — `error_message` (string)

**Behavior by Card Number**:

| Last Digit | HTTP Status | `authorized` | `authorization_code` |
|---|---|---|---|
| Odd (1,3,5,7,9) | 200 | `true` | Random UUID |
| Even (2,4,6,8) | 200 | `false` | `""` (empty) |
| Zero (0) | 503 | — | — |
| Missing fields | 400 | — | `error_message` |

**Contract source**: `imposters/bank_simulator.ejs` — **do not modify**.

### Payment Gateway API Contract Summary

Full spec: `doc/openapi/payment-gateway.yaml`

**Schemas** (→ generated as Java classes):
- `ProcessPaymentRequest` — `card_number`, `expiry_month`, `expiry_year`, `currency` (enum: GBP/USD/EUR), `amount`, `cvv`
- `ProcessPaymentResponse` — `id`, `status` (enum: Authorized/Declined/Rejected), `cardNumberLastFour`, `expiryMonth`, `expiryYear`, `currency`, `amount`
- `PaymentDetailsResponse` — same fields, status only Authorized/Declined
- `ErrorResponse` — `message`

**Endpoints** (→ generated as `PaymentsApi` interface):

| Method | Path | Statuses | Covers |
|---|---|---|---|
| `POST` | `/payment` | 200 (Authorized/Declined), 400 (Rejected), 429 (Rate limited), 502 (Bank error) | **FR-1** |
| `GET` | `/payment/{id}` | 200 (Found), 404 (Not found), 429 (Rate limited) | **FR-2** |
