# AGENTS.md — BNPL Platform MVP Specification & Engineering Rules

## 1. Project Overview & Scope
Build a fault-tolerant, production-grade **BNPL (Buy Now, Pay Later)** MVP platform using a microservices architecture in Java.
The system splits customer purchases into **4 equal installments (25% every 2 weeks)**. The 1st installment (25%) is charged synchronously during checkout; the remaining 3 installments are scheduled and charged asynchronously via background jobs and Kafka events.

### MVP Services (3 Core Microservices)
1. **`checkout-service` (Port 8081):** Order creation, sync call to `payment-service` for the 1st share, Transactional Outbox publishing `ORDER_AUTHORIZED`.
2. **`payment-service` (Port 8082):** Payment intents, Redis idempotency, mocked acquiring bank gateway (with configurable latency/failure simulation), webhook ingress with HMAC-SHA256 verification, Outbox publishing `PAYMENT_SETTLED`. PCI DSS SAQ-A compliant: **never store raw PAN/CVV**, only `card_mask`, `card_token`, and `HMAC-SHA256` blind hashes.
3. **`schedule-service` (Port 8083):** Consumes `ORDER_AUTHORIZED` to generate 4-share repayment schedules (with penny-balancing on the 4th share), runs a distributed nightly billing cron using **ShedLock (Redis)** + **Keyset/Cursor pagination**, updates installment statuses via **Optimistic Locking (`@Version`)**, and processes `PAYMENT_SETTLED` events.

---

## 2. Infrastructure & Local Environment (Docker Compose)
Everything must run locally via a single `docker-compose.yml` (NO Kubernetes).

### Infrastructure Components & Credentials
* **PostgreSQL 16** (Port `5432`, DB: `bnpl_db`, User: `bnpl_admin`, Password: `BnplSuperSecretDbPass2026!`)
  * Single Postgres instance with **isolated schemas** initialized via `init.sql`:
    * `checkout_schema` (owned/used by `checkout-service`)
    * `payment_schema` (owned/used by `payment-service`)
    * `schedule_schema` (owned/used by `schedule-service`)
* **Redis 7** (Port `6379`, Password: `BnplRedisSecretPass2026!`)
  * Used for payment idempotency keys (`SETNX` / atomic Lua or Spring Data Redis) and `ShedLock` distributed locks.
* **Apache Kafka (KRaft mode, no Zookeeper)** (Port `9092`, UI on `8080` via Provectus Kafka UI)
  * Topics: `bnpl.orders.events`, `bnpl.payments.commands`, `bnpl.payments.results`, `bnpl.dlq`.
* **Mock Acquiring Bank:** Implemented either as a dedicated lightweight Spring Boot profile/controller inside `payment-service` (`/mock-bank/v1/charge`) or a WireMock container on port `8089` with deterministic failure triggers (e.g., card mask ending in `0000` triggers `INSUFFICIENT_FUNDS`, `9999` triggers 3s timeout).
* **Shared Secrets (for local dev/docker-compose):**
  * `HMAC_WEBHOOK_SECRET`: `whsec_9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c`
  * `CARD_BLIND_INDEX_PEPPER`: `pep_1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p`

---

## 3. Tech Stack & Strict Codebase Style Rules

### Stack
* **Java 21** & **Spring Boot 3.2.x / 3.3.x** (Maven multi-module project: `bnpl-parent` -> `checkout-service`, `payment-service`, `schedule-service`, `common-contracts`).
* **Persistence:** Spring Data JPA / **Hibernate 6**, **Liquibase** (separate changelog per service targeting its own schema).
* **Resilience:** Resilience4j (Timeouts, CircuitBreaker, RateLimiter for bank calls).
* **Testing:** JUnit 5, AssertJ, **Testcontainers** (PostgreSQL, Redis, Kafka), WireMock.

### Mandatory Coding Standards (Zero Tolerance)
1. **NO LOMBOK:** Do not include `org.projectlombok:lombok` anywhere. Write explicit constructors, getters, or use Java Records.
2. **Java Records Everywhere:**
   * All REST Request/Response DTOs must be `record`s.
   * All Kafka Event payloads must be `record`s.
   * All `@ConfigurationProperties` classes must be `record`s (e.g., `@ConfigurationProperties(prefix = "bnpl.acquiring") public record AcquiringProperties(...) {}`).
3. **Jakarta Validation:** Every incoming REST Request `record` must use `jakarta.validation.constraints.*` (`@NotNull`, `@Positive`, `@NotBlank`, `@Pattern`, `@Valid`) and controllers must enforce `@Valid`.
4. **JPA Entity Rules (Hibernate):**
   * Entities CANNOT be Java `record`s (use standard classes with protected no-arg constructors).
   * Explicitly define `@Version` on entities subject to concurrent updates (`InstallmentEntity`, `BnplOrderEntity`).
   * **Prevent N+1 Queries:** Default all `@OneToMany` / `@ManyToOne` associations to `FetchType.LAZY`. Use `@EntityGraph` or explicit `JOIN FETCH` queries when loading aggregates. Enable `hibernate.default_batch_fetch_size=20`.
   * **Strict Transaction Boundaries:** NEVER open a `@Transactional` block around external HTTP calls (e.g., `checkout-service` calling `payment-service`, or `payment-service` calling the Mock Bank). External network I/O must happen *outside* database transactions to prevent HikariCP connection pool exhaustion.
5. **Pagination Rule:** NEVER use `OFFSET` / `LIMIT` pagination for background batch processing or large tables. Always use **Keyset / Cursor Pagination** (`WHERE status = 'PENDING' AND due_date <= :today AND id > :lastSeenId ORDER BY id ASC LIMIT :batchSize`).
6. **Financial Math:** Always use `BigDecimal` (`NUMERIC(19, 2)` in Postgres) with `RoundingMode.HALF_UP`. The 4th installment must always be calculated as `totalAmount - (share1 + share2 + share3)` (penny-balancing invariant).

---

## 4. Database Schemas & Domain Entities (Liquibase)

### `checkout_schema`
* `bnpl_orders`: `id (UUID PK)`, `merchant_order_id (VARCHAR UNIQUE)`, `user_id (UUID)`, `total_amount (NUMERIC(19,2))`, `status (VARCHAR: NEW, PAYMENT_PROCESSING, AUTHORIZED, REJECTED)`, `version (BIGINT)`, `created_at (TIMESTAMPTZ)`.
* `outbox_messages`: `id (UUID PK)`, `aggregate_type (VARCHAR)`, `aggregate_id (UUID)`, `topic (VARCHAR)`, `payload (JSONB)`, `status (VARCHAR: PENDING, SENT)`, `created_at (TIMESTAMPTZ)`.

### `payment_schema`
* `payment_intents`: `id (UUID PK)`, `idempotency_key (VARCHAR UNIQUE)`, `order_id (UUID)`, `installment_id (UUID NULL)`, `amount (NUMERIC(19,2))`, `card_mask (VARCHAR)`, `card_token (VARCHAR)`, `card_blind_hash (VARCHAR)`, `status (VARCHAR: CREATED, PROCESSING, SUCCEEDED, FAILED)`, `external_tx_id (VARCHAR)`, `created_at (TIMESTAMPTZ)`.
* `outbox_messages`: Same structure as above.

### `schedule_schema`
* `payment_schedules`: `id (UUID PK)`, `order_id (UUID UNIQUE)`, `user_id (UUID)`, `total_amount (NUMERIC(19,2))`, `remaining_debt (NUMERIC(19,2))`, `card_token (VARCHAR)`, `status (VARCHAR: ACTIVE, OVERDUE, COMPLETED)`, `version (BIGINT)`, `created_at (TIMESTAMPTZ)`.
* `installments`: `id (UUID PK)`, `schedule_id (UUID FK)`, `installment_number (INT: 1..4)`, `amount (NUMERIC(19,2))`, `due_date (DATE)`, `status (VARCHAR: PAID, PENDING, PROCESSING, GRACE_PERIOD, OVERDUE)`, `version (BIGINT)`.
  * *Index required:* `CREATE INDEX idx_installments_due_cursor ON schedule_schema.installments (status, due_date, id);`
* `shedlock`: Standard ShedLock table (`name VARCHAR(64) PK`, `lock_until TIMESTAMP`, `locked_at TIMESTAMP`, `locked_by VARCHAR(255)`).

---

## 5. Step-by-Step Execution Plan & Git Commit Protocol

You MUST execute this project incrementally in phases. **After completing and verifying each phase (including passing tests), create a clean, atomic Git commit** using Conventional Commits (`feat:`, `chore:`, `test:`).

* **Phase 1: Skeleton & Infra**
  * Initialize Maven multi-module project (`pom.xml`), `docker-compose.yml`, Postgres `init.sql` (creating the 3 schemas), and shared `common-contracts` (Kafka event records & HMAC utilities).
  * *Commit:* `chore: setup multi-module maven structure and docker-compose infrastructure`
* **Phase 2: `payment-service` + Mock Bank**
  * Implement Liquibase migrations, JPA entities, Redis idempotency filter/service, Mock Bank endpoint, HMAC webhook verification, Outbox poller, and Unit + Testcontainers integration tests.
  * *Commit:* `feat(payment): implement payment processing, redis idempotency, and mock acquiring`
* **Phase 3: `checkout-service`**
  * Implement order creation, synchronous HTTP call to `payment-service` (strictly outside `@Transactional`), local DB commit + Outbox save, and integration tests with WireMock + Testcontainers.
  * *Commit:* `feat(checkout): implement sync first-share checkout flow with transactional outbox`
* **Phase 4: `schedule-service` & Nightly Billing**
  * Implement Kafka consumer for `ORDER_AUTHORIZED` (generating 4 installments with penny-balancing), ShedLock nightly scheduler with keyset cursor pagination publishing charge tasks to Kafka, and `PAYMENT_SETTLED` consumer updating installments with `@Version` optimistic locking.
  * *Commit:* `feat(schedule): implement repayment schedules, shedlock cursor billing, and optimistic locking`
* **Phase 5: End-to-End Verification**
  * Create a `README.md` with `curl` commands demonstrating: (1) Checkout flow, (2) Idempotency duplicate rejection, (3) Triggering the scheduler to charge installment #2, and (4) Verifying final balances in Postgres.
  * *Commit:* `docs: add architecture guide and end-to-end verification scenarios`

---

## 6. Agent Context Management & Self-Verification Tactics

To prevent context window pollution, hallucinations, and broken builds, strictly follow these rules:
1. **Work One Service at a Time:** Never generate code for all 3 services in a single turn. Complete, compile (`mvn test-compile`), and run tests (`mvn test`) for one module before loading files of the next module.
2. **Maintain `PROGRESS.md`:** Maintain a concise `PROGRESS.md` file at the repo root. At the end of each phase, log completed endpoints, Kafka topic names, and database tables so you do not need to re-read previously written services to recall DTO field names.
3. **Verify SQL & Hibernate Behavior in Tests:** In integration tests, enable `spring.jpa.properties.hibernate.generate_statistics=true` or use `datasource-proxy` to assert query counts and verify zero N+1 issues and proper optimistic locking behavior (`ObjectOptimisticLockingFailureException`).
4. **Compact Error Logs:** When running Maven builds or tests, pipe output or inspect only failure stack traces rather than dumping thousands of lines of Spring Boot startup logs into context.