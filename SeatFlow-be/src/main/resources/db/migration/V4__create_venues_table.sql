CREATE TABLE venues (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(120) NOT NULL,
    country VARCHAR(120) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT venues_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT venues_address_not_blank CHECK (length(trim(address)) > 0),
    CONSTRAINT venues_city_not_blank CHECK (length(trim(city)) > 0),
    CONSTRAINT venues_country_not_blank CHECK (length(trim(country)) > 0),
    CONSTRAINT venues_timezone_not_blank CHECK (length(trim(timezone)) > 0),
    CONSTRAINT venues_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX venues_status_idx
    ON venues (status);

CREATE INDEX venues_name_id_idx
    ON venues (name, id);
