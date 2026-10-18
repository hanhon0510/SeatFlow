-- Per-event reminder settings. An event without a row uses the application default.
CREATE TABLE event_reminder_settings (
    event_id UUID PRIMARY KEY REFERENCES events (id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL,
    hours_before INTEGER NOT NULL,
    updated_by UUID REFERENCES users (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT event_reminder_settings_hours_range CHECK (hours_before BETWEEN 1 AND 336)
);

-- One row per order that has been reminded, so a reminder is never sent twice.
CREATE TABLE order_reminders (
    order_id UUID PRIMARY KEY REFERENCES orders (id) ON DELETE CASCADE,
    sent_at TIMESTAMPTZ NOT NULL
);
