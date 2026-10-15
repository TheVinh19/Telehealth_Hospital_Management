CREATE TABLE IF NOT EXISTS specialties (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS doctors (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    specialty_id UUID NOT NULL REFERENCES specialties(id),
    display_name VARCHAR(255) NOT NULL,
    bio TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS slots (
    id UUID PRIMARY KEY,
    doctor_id UUID NOT NULL REFERENCES doctors(id),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_doctor_slot UNIQUE(doctor_id, starts_at)
);

CREATE TABLE IF NOT EXISTS appointments (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    doctor_id UUID NOT NULL REFERENCES doctors(id),
    slot_id UUID NOT NULL REFERENCES slots(id),
    intake_id UUID NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_PAYMENT',
    amount_vnd BIGINT NOT NULL DEFAULT 100000,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    version INT NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS slot_claims (
    slot_id UUID PRIMARY KEY REFERENCES slots(id),
    appointment_id UUID NOT NULL UNIQUE REFERENCES appointments(id),
    kind VARCHAR(50) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS reschedule_history (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL REFERENCES appointments(id),
    old_slot_id UUID NOT NULL REFERENCES slots(id),
    new_slot_id UUID NOT NULL REFERENCES slots(id),
    old_starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    new_starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_id UUID NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    appointment_version INT NOT NULL
);

CREATE TABLE IF NOT EXISTS idempotency_requests (
    patient_id UUID NOT NULL,
    request_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(255) NOT NULL,
    appointment_id UUID REFERENCES appointments(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(patient_id, request_key)
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

CREATE TABLE IF NOT EXISTS inbox_events (
    consumer_name VARCHAR(255) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(consumer_name, event_id)
);
