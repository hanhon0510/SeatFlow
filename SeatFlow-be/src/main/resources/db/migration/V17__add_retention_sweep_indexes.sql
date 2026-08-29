-- Supports the scheduled retention sweep. outbox_events_pending_idx is partial on PENDING, so
-- the publisher's own index cannot serve the cleanup scan over PUBLISHED rows.
CREATE INDEX IF NOT EXISTS outbox_events_published_cleanup_idx
    ON outbox_events (published_at)
    WHERE status = 'PUBLISHED';

CREATE INDEX IF NOT EXISTS processed_events_processed_at_idx
    ON processed_events (processed_at);

CREATE INDEX IF NOT EXISTS refresh_tokens_expires_at_idx
    ON refresh_tokens (expires_at);
