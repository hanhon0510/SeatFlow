-- Who admitted the holder. NULL for every ticket that was never scanned.
ALTER TABLE tickets
    ADD COLUMN checked_in_by UUID REFERENCES users (id),
    ADD CONSTRAINT tickets_check_in_consistent CHECK (
        (status = 'USED') = (used_at IS NOT NULL)
    );

-- The door screen lists the latest admissions of one event.
CREATE INDEX tickets_used_at_idx
    ON tickets (used_at DESC, id DESC)
    WHERE status = 'USED';
