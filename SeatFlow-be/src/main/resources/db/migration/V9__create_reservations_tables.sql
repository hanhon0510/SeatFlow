CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    event_id UUID NOT NULL REFERENCES events (id),
    hold_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT reservations_hold_id_uq UNIQUE (hold_id),
    CONSTRAINT reservations_status_check CHECK (
        status IN ('PENDING_PAYMENT', 'CONFIRMED', 'PAYMENT_FAILED', 'EXPIRED', 'CANCELLED')
    ),
    CONSTRAINT reservations_total_amount_non_negative CHECK (total_amount >= 0),
    CONSTRAINT reservations_expiration_after_creation CHECK (expires_at >= created_at),
    CONSTRAINT reservations_update_after_creation CHECK (updated_at >= created_at)
);

CREATE INDEX reservations_user_created_idx
    ON reservations (user_id, created_at DESC, id);

CREATE INDEX reservations_event_id_idx
    ON reservations (event_id);

CREATE INDEX reservations_status_expiration_idx
    ON reservations (status, expires_at);

CREATE TABLE reservation_items (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservations (id) ON DELETE CASCADE,
    event_seat_id UUID NOT NULL REFERENCES event_seats (id),
    price NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT reservation_items_reservation_seat_uq UNIQUE (reservation_id, event_seat_id),
    CONSTRAINT reservation_items_price_non_negative CHECK (price >= 0)
);

CREATE INDEX reservation_items_event_seat_id_idx
    ON reservation_items (event_seat_id);
