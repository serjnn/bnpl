-- Initialize schemas for BNPL Microservices
CREATE SCHEMA IF NOT EXISTS checkout_schema;
CREATE SCHEMA IF NOT EXISTS payment_schema;
CREATE SCHEMA IF NOT EXISTS schedule_schema;

-- Grant permissions to bnpl_admin
GRANT ALL ON SCHEMA checkout_schema TO bnpl_admin;
GRANT ALL ON SCHEMA payment_schema TO bnpl_admin;
GRANT ALL ON SCHEMA schedule_schema TO bnpl_admin;

-- Set search path defaults if needed
ALTER USER bnpl_admin SET search_path TO public, checkout_schema, payment_schema, schedule_schema;
