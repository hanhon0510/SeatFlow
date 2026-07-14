-- SeatFlow schema bootstrap placeholder.
-- Replace with domain tables as features are added.
CREATE TABLE IF NOT EXISTS schema_bootstrap (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
