CREATE TABLE event_seats (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    seat_id UUID NOT NULL REFERENCES seats (id) ON DELETE CASCADE,
    price NUMERIC(12, 2) NOT NULL,
    permanent_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT event_seats_event_seat_uq UNIQUE (event_id, seat_id),
    CONSTRAINT event_seats_price_non_negative CHECK (price >= 0),
    CONSTRAINT event_seats_status_check CHECK (permanent_status IN ('AVAILABLE', 'SOLD', 'BLOCKED')),
    CONSTRAINT event_seats_version_non_negative CHECK (version >= 0)
);

CREATE INDEX event_seats_event_id_idx
    ON event_seats (event_id);

CREATE INDEX event_seats_seat_id_idx
    ON event_seats (seat_id);

CREATE INDEX event_seats_event_status_idx
    ON event_seats (event_id, permanent_status, seat_id);
