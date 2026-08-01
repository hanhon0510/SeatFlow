CREATE TABLE events (
    id UUID PRIMARY KEY,
    venue_id UUID NOT NULL REFERENCES venues (id),
    name VARCHAR(180) NOT NULL,
    description TEXT,
    start_time TIMESTAMPTZ NOT NULL,
    sales_start_time TIMESTAMPTZ NOT NULL,
    sales_end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT events_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT events_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT events_sales_start_before_start CHECK (sales_start_time < start_time),
    CONSTRAINT events_sales_end_not_after_start CHECK (sales_end_time <= start_time)
);

CREATE INDEX events_venue_id_idx
    ON events (venue_id);

CREATE INDEX events_status_start_time_idx
    ON events (status, start_time, id);

CREATE INDEX events_start_name_id_idx
    ON events (start_time, name, id);
