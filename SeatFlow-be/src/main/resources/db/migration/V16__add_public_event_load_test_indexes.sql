CREATE INDEX IF NOT EXISTS events_published_start_name_id_idx
    ON events (start_time, name, id)
    WHERE status = 'PUBLISHED';

CREATE INDEX IF NOT EXISTS event_seats_available_event_price_idx
    ON event_seats (event_id, price)
    WHERE permanent_status = 'AVAILABLE';
