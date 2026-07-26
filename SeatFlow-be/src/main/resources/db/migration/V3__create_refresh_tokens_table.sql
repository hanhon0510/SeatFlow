CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT refresh_tokens_token_hash_not_blank CHECK (length(trim(token_hash)) > 0)
);

CREATE UNIQUE INDEX refresh_tokens_token_hash_uq
    ON refresh_tokens (token_hash);

CREATE INDEX refresh_tokens_user_id_idx
    ON refresh_tokens (user_id);
