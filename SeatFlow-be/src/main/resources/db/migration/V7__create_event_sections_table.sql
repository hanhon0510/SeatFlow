CREATE TABLE event_sections (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    venue_section_id UUID NOT NULL REFERENCES venue_sections (id) ON DELETE CASCADE,
    price NUMERIC(12, 2) NOT NULL,
    sales_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT event_sections_price_non_negative CHECK (price >= 0),
    CONSTRAINT event_sections_event_section_uq UNIQUE (event_id, venue_section_id)
);

CREATE INDEX event_sections_event_id_idx
    ON event_sections (event_id);

CREATE INDEX event_sections_venue_section_id_idx
    ON event_sections (venue_section_id);
