-- Who may use the ticket. It starts as the buyer and changes when the ticket is passed on.
ALTER TABLE tickets
    ADD COLUMN holder_user_id UUID REFERENCES users (id);

UPDATE tickets
SET holder_user_id = purchase_order.user_id
FROM orders purchase_order
WHERE purchase_order.id = tickets.order_id;

ALTER TABLE tickets
    ALTER COLUMN holder_user_id SET NOT NULL;

CREATE INDEX tickets_holder_user_id_idx
    ON tickets (holder_user_id);

-- Whether holders may pass tickets of this event on to another account.
ALTER TABLE events
    ADD COLUMN transfers_allowed BOOLEAN NOT NULL DEFAULT TRUE;

-- Every hand-over, kept for staff: which ticket, who gave it and who got it.
CREATE TABLE ticket_transfers (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    from_user_id UUID NOT NULL REFERENCES users (id),
    to_user_id UUID NOT NULL REFERENCES users (id),
    transferred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ticket_transfers_different_users CHECK (from_user_id <> to_user_id)
);

CREATE INDEX ticket_transfers_ticket_id_idx
    ON ticket_transfers (ticket_id, transferred_at DESC);
