-- People waiting for seats at a sold-out event. A row moves WAITING -> OFFERED when seats free up
-- and they are told, then ends as FULFILLED (they started a hold on the offered seats), EXPIRED
-- (they did not in time, or the sale closed) or CANCELLED (they left the list).
CREATE TABLE waitlist_entries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id),
    seat_count INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    offered_at TIMESTAMPTZ,
    offer_expires_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT waitlist_entries_status_check CHECK (
        status IN ('WAITING', 'OFFERED', 'FULFILLED', 'EXPIRED', 'CANCELLED')
    ),
    CONSTRAINT waitlist_entries_seat_count_positive CHECK (seat_count > 0),
    CONSTRAINT waitlist_entries_offer_has_deadline CHECK (
        status <> 'OFFERED' OR (offered_at IS NOT NULL AND offer_expires_at IS NOT NULL)
    )
);

-- A user is on an event's list at most once at a time; finished entries do not count.
CREATE UNIQUE INDEX waitlist_entries_active_user_uq
    ON waitlist_entries (event_id, user_id)
    WHERE status IN ('WAITING', 'OFFERED');

-- The queue itself: first come, first served.
CREATE INDEX waitlist_entries_queue_idx
    ON waitlist_entries (event_id, created_at, id)
    WHERE status = 'WAITING';

CREATE INDEX waitlist_entries_offer_expiry_idx
    ON waitlist_entries (offer_expires_at)
    WHERE status = 'OFFERED';

-- Looked up on every seat hold and seat map read, so it covers only the rows that matter.
CREATE INDEX waitlist_entries_open_offer_idx
    ON waitlist_entries (event_id)
    WHERE status = 'OFFERED';

-- The specific seats set aside for an offer. Nobody else can hold them until the offer ends.
CREATE TABLE waitlist_offer_seats (
    entry_id UUID NOT NULL REFERENCES waitlist_entries (id) ON DELETE CASCADE,
    event_seat_id UUID NOT NULL REFERENCES event_seats (id) ON DELETE CASCADE,
    PRIMARY KEY (entry_id, event_seat_id)
);

CREATE INDEX waitlist_offer_seats_event_seat_idx
    ON waitlist_offer_seats (event_seat_id);
