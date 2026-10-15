ALTER TABLE orders DROP CONSTRAINT orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED', 'REFUNDED'));

ALTER TABLE payments DROP CONSTRAINT payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING', 'SUCCEEDED', 'DECLINED', 'TIMED_OUT', 'FAILED', 'REFUNDED'));

-- One row per refunded order: the unique key is what makes a repeated refund request a no-op
-- instead of a second payout.
CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id),
    payment_id UUID NOT NULL REFERENCES payments (id),
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reason VARCHAR(255),
    refunded_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT refunds_order_uq UNIQUE (order_id),
    CONSTRAINT refunds_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT refunds_currency_check CHECK (currency ~ '^[A-Z]{3}$')
);

ALTER TABLE order_paid_analytics ADD COLUMN refunded_at TIMESTAMPTZ;
