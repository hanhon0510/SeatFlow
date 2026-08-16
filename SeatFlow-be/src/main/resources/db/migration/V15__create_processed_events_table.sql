CREATE TABLE processed_events (
    id UUID PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT processed_events_consumer_event_uq UNIQUE (consumer_name, event_id)
);

CREATE INDEX processed_events_event_idx
    ON processed_events (event_id);

CREATE TABLE order_paid_analytics (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    order_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    user_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    seat_count INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT order_paid_analytics_event_uq UNIQUE (event_id),
    CONSTRAINT order_paid_analytics_amount_non_negative CHECK (total_amount >= 0),
    CONSTRAINT order_paid_analytics_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT order_paid_analytics_seat_count_check CHECK (seat_count > 0)
);

CREATE INDEX order_paid_analytics_order_idx
    ON order_paid_analytics (order_id);
