# BNPL (Buy Now, Pay Later) MVP Platform

Production-grade, fault-tolerant **Buy Now, Pay Later (BNPL)** platform architecture built with Java 21, Spring Boot 3.3.x, PostgreSQL 16, Redis 7, Apache Kafka (KRaft), and ShedLock.

---

## Architecture Overview

The system splits customer purchases into **4 installments (25% every 2 weeks)**:
* **1st Installment (25%):** Charged synchronously during checkout.
* **Installments 2, 3, 4 (25% each):** Scheduled at +14, +28, and +42 days, billed asynchronously via a distributed nightly cron job with **Keyset/Cursor pagination** and Kafka events. The 4th installment enforces the **Penny-Balancing Invariant** (`totalAmount - (share1 + share2 + share3)`).

```
                      +-------------------+
                      |   Client / Web    |
                      +---------+---------+
                                |
             1. POST /v1/orders/checkout ($100.00)
                                v
                   +-------------------------+
                   |    checkout-service     | (Port 8081)
                   |  (Schema: checkout_db)  |
                   +------------+------------+
                                |
             2. Sync HTTP POST /v1/payments/charge (1st share: $25.00)
                [Outside @Transactional boundary]
                                v
                   +-------------------------+
                   |     payment-service     | (Port 8082)
                   |   (Schema: payment_db)  |
                   +------------+------------+
                                |
        3. Calls Mock Bank Gateway (/mock-bank/v1/charge)
        4. Writes PAYMENT_SETTLED to Outbox
                                |
        5. Returns HTTP 200 OK to checkout-service
                                |
        6. checkout-service writes ORDER_AUTHORIZED to Outbox
                                |
                                v
        =================== Apache Kafka ===================
          Topics:
            - bnpl.orders.events
            - bnpl.payments.commands
            - bnpl.payments.results
        ====================================================
                                |
        7. Consumes ORDER_AUTHORIZED
                                v
                   +-------------------------+
                   |    schedule-service     | (Port 8083)
                   |  (Schema: schedule_db)  |
                   +-------------------------+
                     - Generates 4 installments with Penny-Balancing
                     - Distributed ShedLock Nightly Billing Job
                     - Keyset/Cursor Pagination (No OFFSET/LIMIT)
                     - Optimistic Locking (@Version)
```

---

## 3 Core Microservices

| Service | Port | Database Schema | Key Responsibilities |
| :--- | :--- | :--- | :--- |
| **`checkout-service`** | `8081` | `checkout_schema` | Order lifecycle, synchronous 1st-share payment orchestration, Transactional Outbox publishing `ORDER_AUTHORIZED`. |
| **`payment-service`** | `8082` | `payment_schema` | Payment intents, Redis idempotency (`SETNX`), Mock Acquiring Bank, HMAC-SHA256 webhook ingress, Outbox publishing `PAYMENT_SETTLED`. PCI DSS SAQ-A compliant (raw PAN/CVV is never stored). |
| **`schedule-service`** | `8083` | `schedule_schema` | Consumes `ORDER_AUTHORIZED`, generates 4-share schedules with penny-balancing, ShedLock distributed nightly billing cron with keyset pagination, `@Version` optimistic locking, and `PAYMENT_SETTLED` consumption. |

---

## Architectural Rules & Standards Enforced

1. **NO LOMBOK:** Zero Lombok usage across the entire project. Standard constructors, getters, and records are used.
2. **Java Records Everywhere:** All REST Request/Response DTOs, Kafka Event payloads, and `@ConfigurationProperties` are Java records.
3. **Jakarta Validation:** Incoming requests are strictly validated (`@NotNull`, `@Positive`, `@NotBlank`).
4. **Strict Transaction Boundaries:** Network I/O (e.g. `checkout-service` calling `payment-service`, or `payment-service` calling the acquiring bank) is executed **strictly outside `@Transactional` blocks** to prevent HikariCP pool starvation.
5. **Keyset / Cursor Pagination:** Background billing jobs use Keyset Cursor Pagination (`WHERE status = 'PENDING' AND due_date <= :today AND id > :lastSeenId ORDER BY id ASC LIMIT :batchSize`) instead of slow, resource-heavy `OFFSET / LIMIT` queries.
6. **Financial Precision:** Exact `BigDecimal` arithmetic with `RoundingMode.HALF_UP` (`NUMERIC(19, 2)`). The 4th installment always balances pennies:
   $$\text{Share}_4 = \text{Total} - (\text{Share}_1 + \text{Share}_2 + \text{Share}_3)$$
7. **PCI DSS SAQ-A Compliance:** Raw card numbers (PAN) and CVV are **never saved to the database**. Only `card_mask` (`411122******4444`), opaque `card_token`, and `HMAC-SHA256` blind hash are persisted.

---

## Local Infrastructure Setup (Docker Compose)

Start the shared infrastructure (PostgreSQL 16, Redis 7, Apache Kafka KRaft, Kafka UI):

```bash
docker-compose up -d
```

### Components & Ports
* **PostgreSQL 16:** `localhost:5432` (DB: `bnpl_db`, User: `bnpl_admin`, Pass: `BnplSuperSecretDbPass2026!`)
  * Automatically creates schemas: `checkout_schema`, `payment_schema`, `schedule_schema`.
* **Redis 7:** `localhost:6379` (Pass: `BnplRedisSecretPass2026!`)
* **Apache Kafka (KRaft mode):** `localhost:9092`
* **Kafka UI:** `http://localhost:8080`

---

## Building and Running the Services

### Build all modules and run tests:
```bash
mvn clean install
```

### Start the Microservices (separate terminals):
```bash
# Terminal 1: payment-service (Port 8082)
mvn spring-boot:run -pl payment-service

# Terminal 2: checkout-service (Port 8081)
mvn spring-boot:run -pl checkout-service

# Terminal 3: schedule-service (Port 8083)
mvn spring-boot:run -pl schedule-service
```

---

## End-to-End Verification Scenarios

### Scenario 1: Successful Checkout Flow ($100.00 Order)

Create a $100.00 purchase. The system charges the 1st installment ($25.00) synchronously, authorizes the order, and publishes an outbox event to generate the schedule.

```bash
curl -X POST http://localhost:8081/v1/orders/checkout \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "11111111-1111-1111-1111-111111111111",
    "merchantOrderId": "ORDER-DEMO-001",
    "totalAmount": 100.00,
    "pan": "4111222233334444"
  }'
```

**Expected Response (HTTP 200):**
```json
{
  "orderId": "...",
  "merchantOrderId": "ORDER-DEMO-001",
  "userId": "11111111-1111-1111-1111-111111111111",
  "totalAmount": 100.00,
  "firstShareAmount": 25.00,
  "status": "AUTHORIZED",
  "paymentExternalTxId": "tx_bank_...",
  "cardToken": "tok_...",
  "createdAt": "..."
}
```

---

### Scenario 2: Idempotency & Duplicate Request Protection

Resend the exact same checkout request:

```bash
curl -X POST http://localhost:8081/v1/orders/checkout \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "11111111-1111-1111-1111-111111111111",
    "merchantOrderId": "ORDER-DEMO-001",
    "totalAmount": 100.00,
    "pan": "4111222233334444"
  }'
```

**Result:** The system detects the existing order, prevents any duplicate payment call, and returns the existing order details with HTTP 200.

---

### Scenario 3: Deterministic Bank Failure Simulation (Insufficient Funds)

Using a test card ending in `0000` triggers the Mock Acquiring Bank's deterministic decline:

```bash
curl -X POST http://localhost:8081/v1/orders/checkout \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "11111111-1111-1111-1111-111111111111",
    "merchantOrderId": "ORDER-FAIL-001",
    "totalAmount": 50.00,
    "pan": "4111222233330000"
  }'
```

**Expected Response:**
```json
{
  "orderId": "...",
  "merchantOrderId": "ORDER-FAIL-001",
  "totalAmount": 50.00,
  "firstShareAmount": 12.50,
  "status": "REJECTED"
}
```

---

### Scenario 4: Repayment Schedule & Penny-Balancing Invariant Check

Fetch the repayment schedule for the authorized order:

```bash
curl http://localhost:8083/v1/schedules/<ORDER_ID>
```

**Expected Response:**
* Total Amount: `100.00`
* Remaining Debt: `75.00`
* 4 Installments:
  * Installment 1: `$25.00`, Status: `PAID`, Due Date: Today
  * Installment 2: `$25.00`, Status: `PENDING`, Due Date: +14 days
  * Installment 3: `$25.00`, Status: `PENDING`, Due Date: +28 days
  * Installment 4: `$25.00`, Status: `PENDING`, Due Date: +42 days (Penny-Balanced)

*(Note: For total amount $100.03, shares are calculated as `$25.01`, `$25.01`, `$25.01`, and `$25.00`, perfectly balancing to $100.03)*.

---

### Scenario 5: Triggering Distributed Keyset Billing Cron

Trigger the nightly billing job manually to process all due installments via keyset pagination:

```bash
curl -X POST http://localhost:8083/v1/schedules/billing/run
```

**Expected Response:**
```json
{
  "status": "COMPLETED",
  "processedInstallments": 0
}
```

---

### Scenario 6: Webhook Ingress with HMAC-SHA256 Verification

To simulate an asynchronous settlement notification from the acquiring bank:

Payload:
```json
{"paymentIntentId":"<INTENT_UUID>","externalTxId":"tx_bank_settled_999","status":"SUCCEEDED","timestamp":"2026-11-14T12:00:00Z"}
```

Calculate HMAC-SHA256 signature using the secret `whsec_9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c`, then send:

```bash
curl -X POST http://localhost:8082/v1/payments/webhook \
  -H "Content-Type: application/json" \
  -H "X-Signature-SHA256: <CALCULATED_HMAC_HEX>" \
  -d '{"paymentIntentId":"<INTENT_UUID>","externalTxId":"tx_bank_settled_999","status":"SUCCEEDED","timestamp":"2026-11-14T12:00:00Z"}'
```

---

### Scenario 7: PostgreSQL Database Verification

Connect to Postgres to inspect tables across all three isolated schemas:

```bash
docker exec -it bnpl-postgres psql -U bnpl_admin -d bnpl_db
```

```sql
-- Check checkout orders
SELECT id, merchant_order_id, total_amount, status, created_at 
FROM checkout_schema.bnpl_orders;

-- Verify PCI DSS SAQ-A compliance (no PAN stored!)
SELECT id, amount, card_mask, card_token, card_blind_hash, status, external_tx_id 
FROM payment_schema.payment_intents;

-- Check schedules and remaining debt
SELECT id, order_id, total_amount, remaining_debt, status 
FROM schedule_schema.payment_schedules;

-- Check installments and optimistic lock version
SELECT id, installment_number, amount, due_date, status, version 
FROM schedule_schema.installments 
ORDER BY installment_number ASC;
```
