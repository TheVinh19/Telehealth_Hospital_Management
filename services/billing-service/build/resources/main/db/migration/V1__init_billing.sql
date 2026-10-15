CREATE TABLE IF NOT EXISTS payment_orders (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL UNIQUE,
    patient_id UUID NOT NULL,
    amount_vnd BIGINT NOT NULL DEFAULT 100000,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    provider VARCHAR(50) NOT NULL DEFAULT 'MOCK_PAYMENT',
    payment_code VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    provider_transaction_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS payment_callbacks (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES payment_orders(id),
    provider VARCHAR(50) NOT NULL,
    provider_event_id VARCHAR(100) NOT NULL UNIQUE,
    payload_hash VARCHAR(64) NOT NULL,
    result VARCHAR(50) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS outbox_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version INT NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    payload TEXT NOT NULL,
    correlation_id UUID,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
