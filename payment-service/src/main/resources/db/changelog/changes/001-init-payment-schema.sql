--liquibase formatted sql

--changeset bnpl:001-create-payment-intents
CREATE TABLE IF NOT EXISTS payment_intents (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    order_id UUID NOT NULL,
    installment_id UUID,
    amount NUMERIC(19, 2) NOT NULL,
    card_mask VARCHAR(32) NOT NULL,
    card_token VARCHAR(128) NOT NULL,
    card_blind_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    external_tx_id VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_payment_intents_order_id ON payment_intents (order_id);
CREATE INDEX IF NOT EXISTS idx_payment_intents_blind_hash ON payment_intents (card_blind_hash);

--changeset bnpl:002-create-outbox-messages
CREATE TABLE IF NOT EXISTS outbox_messages (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    topic VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created_at ON outbox_messages (status, created_at);
