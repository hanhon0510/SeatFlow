-- Gives the outbox a terminal state. Until now the only statuses were PENDING and PUBLISHED,
-- so an event that could never succeed - a malformed payload, or a type with no topic mapping -
-- was retried forever with no way to record why.
ALTER TABLE outbox_events
    DROP CONSTRAINT outbox_events_status_check;

ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_status_check
    CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'));

ALTER TABLE outbox_events
    DROP CONSTRAINT outbox_events_published_check;

ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_published_check
    CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status IN ('PENDING', 'FAILED') AND published_at IS NULL)
    );

ALTER TABLE outbox_events
    ADD COLUMN failure_reason VARCHAR(500);

ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_failure_reason_check
    CHECK (
        (status = 'FAILED' AND failure_reason IS NOT NULL)
        OR (status <> 'FAILED' AND failure_reason IS NULL)
    );

-- Lets an operator find what was given up on without scanning the whole table.
CREATE INDEX IF NOT EXISTS outbox_events_failed_idx
    ON outbox_events (created_at)
    WHERE status = 'FAILED';
