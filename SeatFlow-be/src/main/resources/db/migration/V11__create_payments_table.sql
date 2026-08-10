CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id),
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    provider_reference VARCHAR(100) NOT NULL,
    failure_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT payments_status_check CHECK (
        status IN ('PENDING', 'SUCCEEDED', 'DECLINED', 'TIMED_OUT', 'FAILED')
    ),
    CONSTRAINT payments_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT payments_provider_reference_uq UNIQUE (provider_reference),
    CONSTRAINT payments_update_after_creation CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX payments_successful_order_uq
    ON payments (order_id)
    WHERE status = 'SUCCEEDED';

CREATE INDEX payments_order_created_idx
    ON payments (order_id, created_at DESC, id DESC);
