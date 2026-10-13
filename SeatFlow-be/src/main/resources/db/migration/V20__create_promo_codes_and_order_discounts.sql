CREATE TABLE promo_codes (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    discount_type VARCHAR(16) NOT NULL,
    discount_value NUMERIC(12, 2) NOT NULL,
    event_id UUID REFERENCES events (id),
    expires_at TIMESTAMPTZ,
    max_uses INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT promo_codes_code_format CHECK (code ~ '^[A-Z0-9]{4,32}$'),
    CONSTRAINT promo_codes_discount_type_check CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT promo_codes_discount_value_check CHECK (
        discount_value > 0 AND (discount_type <> 'PERCENT' OR discount_value <= 100)
    ),
    CONSTRAINT promo_codes_max_uses_check CHECK (max_uses IS NULL OR max_uses > 0),
    CONSTRAINT promo_codes_update_after_creation CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX promo_codes_code_uq ON promo_codes (code);

CREATE INDEX promo_codes_created_idx ON promo_codes (created_at DESC, id DESC);

-- total_amount stays the amount actually charged (payments read it), now net of the discount.
ALTER TABLE orders
    ADD COLUMN promo_code_id UUID REFERENCES promo_codes (id),
    ADD COLUMN promo_code VARCHAR(32),
    ADD COLUMN discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT orders_discount_amount_non_negative CHECK (discount_amount >= 0),
    ADD CONSTRAINT orders_promo_code_consistent CHECK (
        (promo_code_id IS NULL) = (promo_code IS NULL)
    );

-- Redemption counting reads the live PENDING/PAID orders of one code.
CREATE INDEX orders_promo_code_active_idx
    ON orders (promo_code_id)
    WHERE promo_code_id IS NOT NULL AND status IN ('PENDING', 'PAID');
