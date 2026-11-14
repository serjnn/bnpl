# BNPL Platform Implementation Progress

## Completed Phases

### Phase 1: Skeleton & Infrastructure
- [x] Multi-module Maven setup: `bnpl-parent` (`common-contracts`, `payment-service`, `checkout-service`, `schedule-service`).
- [x] Docker Compose setup:
  - PostgreSQL 16 on port `5432` (db: `bnpl_db`, user: `bnpl_admin`) with `init.sql` isolating `checkout_schema`, `payment_schema`, and `schedule_schema`.
  - Redis 7 on port `6379`.
  - Apache Kafka in KRaft mode on port `9092` with topics: `bnpl.orders.events`, `bnpl.payments.commands`, `bnpl.payments.results`, `bnpl.dlq`.
  - Kafka UI on port `8080`.
- [x] `common-contracts`:
  - Enums: `OrderStatus`, `PaymentStatus`, `InstallmentStatus`, `ScheduleStatus`, `OutboxStatus`.
  - Kafka topics: `KafkaTopics`.
  - Event and Command records: `OrderAuthorizedEvent`, `PaymentSettledEvent`, `ProcessPaymentCommand`, `PaymentResultEvent`.
  - Cryptography & PCI DSS SAQ-A utilities: `HmacUtils` (HMAC-SHA256, constant-time verification) and `CardUtils` (masking, blind indexing via HMAC-SHA256 with pepper, card token generation).
  - Unit tests verifying HMAC and card masking/hashing (11 tests passed).

### Phase 2: `payment-service` + Mock Bank
- [x] Liquibase migration for `payment_schema` (`payment_intents`, `outbox_messages`).
- [x] JPA Entities (`PaymentIntentEntity` with `@Version`, `OutboxMessageEntity`).
- [x] Redis idempotency mechanism (`IdempotencyService`).
- [x] Mock Bank endpoint (`/mock-bank/v1/charge`) with deterministic failure triggers:
  - Card mask ending in `0000` -> `INSUFFICIENT_FUNDS`
  - Card mask ending in `9999` -> 3s timeout
- [x] Payment processing service (`PaymentService`):
  - PCI DSS SAQ-A compliance: raw PAN never stored, only mask, token, blind hash.
  - Strict transaction boundary: external HTTP call to Acquiring Bank is strictly outside `@Transactional` blocks!
- [x] Webhook ingress with HMAC-SHA256 verification (`POST /v1/payments/webhook`).
- [x] Transactional Outbox publisher for `PAYMENT_SETTLED` events.
- [x] Kafka command listener for `bnpl.payments.commands`.
- [x] Unit tests for `MockBankController`, `WebhookController`, and `PaymentService` (8 tests passed).

### Phase 3: `checkout-service`
- [x] Liquibase migration for `checkout_schema` (`bnpl_orders`, `outbox_messages`).
- [x] JPA Entities (`BnplOrderEntity` with `@Version`, `OutboxMessageEntity`).
- [x] Synchronous payment integration: `PaymentServiceClient` calling `payment-service` at `/v1/payments/charge`.
- [x] Checkout service (`CheckoutService`):
  - Strict transaction boundary: synchronous call to `payment-service` for 1st share (25%) occurs strictly outside `@Transactional` blocks.
  - Idempotent order processing based on `merchantOrderId`.
  - Penny-rounding calculation with `HALF_UP`.
  - Transactional Outbox publishing `OrderAuthorizedEvent` to `bnpl.orders.events`.
- [x] Outbox publisher poller (`OutboxPublisherService`).
- [x] REST endpoints: `POST /v1/orders/checkout`, `GET /v1/orders/{id}`.
- [x] Unit tests for `CheckoutService` (4 tests passed).

---

## Active & Upcoming Phases

### Phase 4: `schedule-service` & Nightly Billing
- [ ] Liquibase migration for `schedule_schema` (`payment_schedules`, `installments`, `shedlock`).
- [ ] JPA Entities (`PaymentScheduleEntity`, `InstallmentEntity` with `@Version`).
- [ ] Kafka consumer for `ORDER_AUTHORIZED` -> 4 installments generation with penny-balancing on 4th share.
- [ ] Distributed nightly billing scheduler with ShedLock + Keyset/Cursor pagination.
- [ ] Kafka consumer for `PAYMENT_SETTLED` -> installment update with `@Version` optimistic locking.
- [ ] Unit & Integration tests.

### Phase 5: End-to-End Verification & Documentation
- [ ] `README.md` with curl test scripts for checkout, idempotency duplicate rejection, billing cron trigger, balance verification.
