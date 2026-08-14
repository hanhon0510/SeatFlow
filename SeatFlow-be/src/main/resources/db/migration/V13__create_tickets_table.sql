CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id),
    event_seat_id UUID NOT NULL REFERENCES event_seats (id),
    ticket_code VARCHAR(80) NOT NULL,
    status VARCHAR(32) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT tickets_order_event_seat_uq UNIQUE (order_id, event_seat_id),
    CONSTRAINT tickets_ticket_code_uq UNIQUE (ticket_code),
    CONSTRAINT tickets_status_check CHECK (status IN ('ACTIVE', 'USED', 'CANCELLED')),
    CONSTRAINT tickets_code_not_blank CHECK (length(trim(ticket_code)) >= 32),
    CONSTRAINT tickets_used_after_issue CHECK (used_at IS NULL OR used_at >= issued_at)
);

CREATE INDEX tickets_order_id_idx
    ON tickets (order_id);

CREATE INDEX tickets_event_seat_id_idx
    ON tickets (event_seat_id);

CREATE INDEX tickets_status_issued_idx
    ON tickets (status, issued_at DESC, id DESC);
