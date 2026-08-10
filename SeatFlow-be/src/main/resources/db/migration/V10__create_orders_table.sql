CREATE TABLE orders (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservations (id),
    user_id UUID NOT NULL REFERENCES users (id),
    status VARCHAR(32) NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT orders_status_check CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED')),
    CONSTRAINT orders_total_amount_non_negative CHECK (total_amount >= 0),
    CONSTRAINT orders_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT orders_update_after_creation CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX orders_active_reservation_uq
    ON orders (reservation_id)
    WHERE status IN ('PENDING', 'PAID');

CREATE INDEX orders_user_created_idx
    ON orders (user_id, created_at DESC, id DESC);

CREATE INDEX orders_reservation_id_idx
    ON orders (reservation_id);
