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
  - Unit tests verifying HMAC and card masking/hashing.

---

## Active & Upcoming Phases

### Phase 2: `payment-service` + Mock Bank
- [ ] Liquibase migration for `payment_schema` (`payment_intents`, `outbox_messages`).
- [ ] JPA Entities (`PaymentIntentEntity`, `OutboxMessageEntity`).
- [ ] Redis idempotency mechanism (`IdempotencyService`).
- [ ] Mock Bank endpoint (`/mock-bank/v1/charge`) with deterministic failure triggers and latency.
- [ ] Payment processing REST endpoint (`POST /v1/payments/charge`) with PCI DSS SAQ-A compliance (raw PAN never stored).
- [ ] Webhook ingress with HMAC-SHA256 verification (`POST /v1/payments/webhook`).
- [ ] Transactional Outbox publisher for `PAYMENT_SETTLED` events.
- [ ] Unit & Integration tests.

### Phase 3: `checkout-service`
- [ ] Liquibase migration for `checkout_schema` (`bnpl_orders`, `outbox_messages`).
- [ ] JPA Entities (`BnplOrderEntity`, `OutboxMessageEntity`).
- [ ] Checkout endpoint (`POST /v1/orders/checkout`) with strict transaction boundary (sync call to `payment-service` outside `@Transactional`).
- [ ] Transactional Outbox publisher for `ORDER_AUTHORIZED` events.
- [ ] Unit & Integration tests.

### Phase 4: `schedule-service` & Nightly Billing
- [ ] Liquibase migration for `schedule_schema` (`payment_schedules`, `installments`, `shedlock`).
- [ ] JPA Entities (`PaymentScheduleEntity`, `InstallmentEntity` with `@Version`).
- [ ] Kafka consumer for `ORDER_AUTHORIZED` -> 4 installments generation with penny-balancing on 4th share.
- [ ] Distributed nightly billing scheduler with ShedLock + Keyset/Cursor pagination.
- [ ] Kafka consumer for `PAYMENT_SETTLED` -> installment update with `@Version` optimistic locking.
- [ ] Unit & Integration tests.

### Phase 5: End-to-End Verification & Documentation
- [ ] `README.md` with curl test scripts for checkout, idempotency duplicate rejection, billing cron trigger, balance verification.
