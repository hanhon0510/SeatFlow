CREATE TABLE event_cancellations (
    event_id UUID PRIMARY KEY REFERENCES events (id),
    reason VARCHAR(255),
    cancelled_by UUID NOT NULL REFERENCES users (id),
    cancelled_at TIMESTAMPTZ NOT NULL
);
