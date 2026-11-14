--liquibase formatted sql

--changeset bnpl:001-create-payment-schedules
CREATE TABLE IF NOT EXISTS payment_schedules (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    remaining_debt NUMERIC(19, 2) NOT NULL,
    card_token VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_schedules_user_id ON payment_schedules (user_id);
CREATE INDEX IF NOT EXISTS idx_payment_schedules_status ON payment_schedules (status);

--changeset bnpl:002-create-installments
CREATE TABLE IF NOT EXISTS installments (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES payment_schedules(id) ON DELETE CASCADE,
    installment_number INT NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

-- Index required by spec for keyset cursor pagination
CREATE INDEX IF NOT EXISTS idx_installments_due_cursor ON installments (status, due_date, id);

--changeset bnpl:003-create-shedlock
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
