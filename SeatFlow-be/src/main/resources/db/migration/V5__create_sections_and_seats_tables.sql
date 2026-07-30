CREATE TABLE venue_sections (
    id UUID PRIMARY KEY,
    venue_id UUID NOT NULL REFERENCES venues (id),
    name VARCHAR(120) NOT NULL,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT venue_sections_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT venue_sections_display_order_non_negative CHECK (display_order >= 0)
);

CREATE INDEX venue_sections_venue_order_idx
    ON venue_sections (venue_id, display_order, name, id);

CREATE TABLE seats (
    id UUID PRIMARY KEY,
    section_id UUID NOT NULL REFERENCES venue_sections (id) ON DELETE CASCADE,
    row_label VARCHAR(32) NOT NULL,
    seat_number INTEGER NOT NULL,
    seat_label VARCHAR(64) NOT NULL,
    accessible BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT seats_row_label_not_blank CHECK (length(trim(row_label)) > 0),
    CONSTRAINT seats_seat_number_positive CHECK (seat_number > 0),
    CONSTRAINT seats_seat_label_not_blank CHECK (length(trim(seat_label)) > 0),
    CONSTRAINT seats_section_label_uq UNIQUE (section_id, seat_label)
);

CREATE INDEX seats_section_order_idx
    ON seats (section_id, row_label, seat_number, seat_label, id);
