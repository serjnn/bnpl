--liquibase formatted sql

--changeset bnpl:001-create-bnpl-orders
CREATE TABLE IF NOT EXISTS bnpl_orders (
    id UUID PRIMARY KEY,
    merchant_order_id VARCHAR(128) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bnpl_orders_user_id ON bnpl_orders (user_id);
CREATE INDEX IF NOT EXISTS idx_bnpl_orders_status ON bnpl_orders (status);

--changeset bnpl:002-create-checkout-outbox-messages
CREATE TABLE IF NOT EXISTS outbox_messages (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    topic VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_checkout_outbox_status_created_at ON outbox_messages (status, created_at);
