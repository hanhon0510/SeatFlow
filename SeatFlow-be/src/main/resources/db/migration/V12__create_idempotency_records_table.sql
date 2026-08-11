CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    operation VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status INTEGER,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT idempotency_records_scope_uq UNIQUE (user_id, operation, idempotency_key),
    CONSTRAINT idempotency_records_request_hash_check CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT idempotency_records_response_status_check CHECK (
        response_status IS NULL OR response_status BETWEEN 100 AND 599
    ),
    CONSTRAINT idempotency_records_response_check CHECK (
        (response_status IS NULL AND response_body IS NULL)
        OR (response_status IS NOT NULL AND response_body IS NOT NULL)
    ),
    CONSTRAINT idempotency_records_expiration_after_creation CHECK (expires_at > created_at)
);

CREATE INDEX idempotency_records_expiration_idx
    ON idempotency_records (expires_at);
