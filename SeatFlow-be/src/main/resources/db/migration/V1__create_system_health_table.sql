CREATE TABLE system_health (
    id SMALLINT PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT system_health_singleton CHECK (id = 1),
    CONSTRAINT system_health_status_check CHECK (status = 'UP')
);

INSERT INTO system_health (id, status)
VALUES (1, 'UP');

