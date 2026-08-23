BEGIN;

CREATE TEMP TABLE sf048_load_users AS
SELECT id
FROM users
WHERE email LIKE 'sf048-user-%@seatflow.local';

CREATE TEMP TABLE sf048_load_orders AS
SELECT id
FROM orders
WHERE user_id IN (SELECT id FROM sf048_load_users);

CREATE TEMP TABLE sf048_load_reservations AS
SELECT id
FROM reservations
WHERE user_id IN (SELECT id FROM sf048_load_users);

DELETE FROM tickets
WHERE order_id IN (SELECT id FROM sf048_load_orders);

DELETE FROM outbox_events
WHERE aggregate_type = 'Order'
  AND aggregate_id IN (SELECT id FROM sf048_load_orders);

DELETE FROM payments
WHERE order_id IN (SELECT id FROM sf048_load_orders);

DELETE FROM idempotency_records
WHERE user_id IN (SELECT id FROM sf048_load_users);

DELETE FROM orders
WHERE id IN (SELECT id FROM sf048_load_orders);

DELETE FROM reservation_items
WHERE reservation_id IN (SELECT id FROM sf048_load_reservations);

DELETE FROM reservations
WHERE id IN (SELECT id FROM sf048_load_reservations);

DELETE FROM refresh_tokens
WHERE user_id IN (SELECT id FROM sf048_load_users);

DELETE FROM users
WHERE id IN (SELECT id FROM sf048_load_users);

CREATE OR REPLACE FUNCTION pg_temp.sf048_uuid(source text)
RETURNS uuid
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT (
        substr(md5(source), 1, 8) || '-' ||
        substr(md5(source), 9, 4) || '-' ||
        substr(md5(source), 13, 4) || '-' ||
        substr(md5(source), 17, 4) || '-' ||
        substr(md5(source), 21, 12)
    )::uuid
$$;

INSERT INTO venues (
    id,
    name,
    address,
    city,
    country,
    timezone,
    status
)
VALUES (
    pg_temp.sf048_uuid('sf048:venue:main'),
    'SF-048 Load Test Arena',
    '48 Benchmark Avenue',
    'Ho Chi Minh City',
    'Vietnam',
    'Asia/Ho_Chi_Minh',
    'ACTIVE'
)
ON CONFLICT (id) DO UPDATE
SET
    name = EXCLUDED.name,
    address = EXCLUDED.address,
    city = EXCLUDED.city,
    country = EXCLUDED.country,
    timezone = EXCLUDED.timezone,
    status = EXCLUDED.status,
    updated_at = NOW();

WITH section_specs AS (
    SELECT *
    FROM (VALUES
        (1, 'Orchestra'),
        (2, 'Mezzanine'),
        (3, 'Balcony'),
        (4, 'Gallery'),
        (5, 'Box')
    ) AS section_spec(display_order, name)
)
INSERT INTO venue_sections (
    id,
    venue_id,
    name,
    display_order
)
SELECT
    pg_temp.sf048_uuid('sf048:section:' || display_order),
    pg_temp.sf048_uuid('sf048:venue:main'),
    name,
    display_order
FROM section_specs
ON CONFLICT (id) DO UPDATE
SET
    name = EXCLUDED.name,
    display_order = EXCLUDED.display_order;

WITH section_specs AS (
    SELECT *
    FROM (VALUES
        (1, 'Orchestra'),
        (2, 'Mezzanine'),
        (3, 'Balcony'),
        (4, 'Gallery'),
        (5, 'Box')
    ) AS section_spec(display_order, name)
),
seat_specs AS (
    SELECT
        section_specs.display_order,
        row_number,
        seat_number,
        chr(64 + row_number) AS row_label
    FROM section_specs
    CROSS JOIN generate_series(1, 12) row_number
    CROSS JOIN generate_series(1, 10) seat_number
)
INSERT INTO seats (
    id,
    section_id,
    row_label,
    seat_number,
    seat_label,
    accessible
)
SELECT
    pg_temp.sf048_uuid('sf048:seat:' || display_order || ':' || row_label || ':' || seat_number),
    pg_temp.sf048_uuid('sf048:section:' || display_order),
    row_label,
    seat_number,
    row_label || seat_number,
    seat_number = 10
FROM seat_specs
ON CONFLICT (id) DO UPDATE
SET
    row_label = EXCLUDED.row_label,
    seat_number = EXCLUDED.seat_number,
    seat_label = EXCLUDED.seat_label,
    accessible = EXCLUDED.accessible;

WITH event_specs AS (
    SELECT
        pg_temp.sf048_uuid('sf048:event:browse:' || event_number) AS id,
        'SF-048 Browse Event ' || lpad(event_number::text, 2, '0') AS name,
        'Reproducible load-test browsing fixture' AS description,
        TIMESTAMPTZ '2027-05-01 12:00:00+00' + ((event_number - 1) * INTERVAL '1 day') AS start_time
    FROM generate_series(1, 36) event_number
    UNION ALL
    SELECT
        pg_temp.sf048_uuid('sf048:event:hot-seat'),
        'SF-048 Hot Seat Competition',
        'Reproducible load-test hot-seat fixture',
        TIMESTAMPTZ '2027-06-15 12:00:00+00'
    UNION ALL
    SELECT
        pg_temp.sf048_uuid('sf048:event:popular-selection'),
        'SF-048 Popular Event Seat Selection',
        'Reproducible load-test popular-event fixture',
        TIMESTAMPTZ '2027-06-16 12:00:00+00'
    UNION ALL
    SELECT
        pg_temp.sf048_uuid('sf048:event:payment-retries'),
        'SF-048 Payment Retry Event',
        'Reproducible load-test payment retry fixture',
        TIMESTAMPTZ '2027-06-17 12:00:00+00'
)
INSERT INTO events (
    id,
    venue_id,
    name,
    description,
    start_time,
    sales_start_time,
    sales_end_time,
    status
)
SELECT
    id,
    pg_temp.sf048_uuid('sf048:venue:main'),
    name,
    description,
    start_time,
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    start_time - INTERVAL '1 hour',
    'PUBLISHED'
FROM event_specs
ON CONFLICT (id) DO UPDATE
SET
    venue_id = EXCLUDED.venue_id,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    start_time = EXCLUDED.start_time,
    sales_start_time = EXCLUDED.sales_start_time,
    sales_end_time = EXCLUDED.sales_end_time,
    status = EXCLUDED.status,
    updated_at = NOW();

WITH event_specs AS (
    SELECT pg_temp.sf048_uuid('sf048:event:browse:' || event_number) AS event_id
    FROM generate_series(1, 36) event_number
    UNION ALL SELECT pg_temp.sf048_uuid('sf048:event:hot-seat')
    UNION ALL SELECT pg_temp.sf048_uuid('sf048:event:popular-selection')
    UNION ALL SELECT pg_temp.sf048_uuid('sf048:event:payment-retries')
),
section_specs AS (
    SELECT *
    FROM (VALUES
        (1, 125000.00, true),
        (2, 95000.00, true),
        (3, 75000.00, true),
        (4, 55000.00, true),
        (5, 150000.00, true)
    ) AS section_spec(display_order, price, sales_enabled)
)
INSERT INTO event_sections (
    id,
    event_id,
    venue_section_id,
    price,
    sales_enabled
)
SELECT
    pg_temp.sf048_uuid('sf048:event-section:' || event_id || ':' || display_order),
    event_id,
    pg_temp.sf048_uuid('sf048:section:' || display_order),
    price,
    sales_enabled
FROM event_specs
CROSS JOIN section_specs
ON CONFLICT (event_id, venue_section_id) DO UPDATE
SET
    price = EXCLUDED.price,
    sales_enabled = EXCLUDED.sales_enabled;

WITH event_specs AS (
    SELECT pg_temp.sf048_uuid('sf048:event:browse:' || event_number) AS event_id
    FROM generate_series(1, 36) event_number
    UNION ALL SELECT pg_temp.sf048_uuid('sf048:event:hot-seat')
    UNION ALL SELECT pg_temp.sf048_uuid('sf048:event:popular-selection')
    UNION ALL SELECT pg_temp.sf048_uuid('sf048:event:payment-retries')
)
INSERT INTO event_seats (
    id,
    event_id,
    seat_id,
    price,
    permanent_status,
    version
)
SELECT
    pg_temp.sf048_uuid('sf048:event-seat:' || event_specs.event_id || ':' || seat.id),
    event_specs.event_id,
    seat.id,
    event_section.price,
    CASE WHEN event_section.sales_enabled THEN 'AVAILABLE' ELSE 'BLOCKED' END,
    0
FROM event_specs
JOIN event_sections event_section
  ON event_section.event_id = event_specs.event_id
JOIN venue_sections venue_section
  ON venue_section.id = event_section.venue_section_id
JOIN seats seat
  ON seat.section_id = venue_section.id
WHERE venue_section.venue_id = pg_temp.sf048_uuid('sf048:venue:main')
ON CONFLICT (event_id, seat_id) DO UPDATE
SET
    price = EXCLUDED.price,
    permanent_status = EXCLUDED.permanent_status,
    version = 0,
    updated_at = NOW();

DROP TABLE sf048_load_reservations;
DROP TABLE sf048_load_orders;
DROP TABLE sf048_load_users;

COMMIT;
