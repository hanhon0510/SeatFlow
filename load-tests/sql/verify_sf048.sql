WITH sf048_users AS (
    SELECT id
    FROM users
    WHERE email LIKE 'sf048-user-%@seatflow.local'
),
sf048_events AS (
    SELECT id
    FROM events
    WHERE name LIKE 'SF-048%'
),
sf048_event_seats AS (
    SELECT id
    FROM event_seats
    WHERE event_id IN (SELECT id FROM sf048_events)
),
sf048_orders AS (
    SELECT id
    FROM orders
    WHERE user_id IN (SELECT id FROM sf048_users)
),
duplicate_sales AS (
    SELECT ticket.event_seat_id, COUNT(*) AS ticket_count
    FROM tickets ticket
    WHERE ticket.event_seat_id IN (SELECT id FROM sf048_event_seats)
    GROUP BY ticket.event_seat_id
    HAVING COUNT(*) > 1
),
duplicate_successful_payments AS (
    SELECT payment.order_id, COUNT(*) AS payment_count
    FROM payments payment
    WHERE payment.order_id IN (SELECT id FROM sf048_orders)
      AND payment.status = 'SUCCEEDED'
    GROUP BY payment.order_id
    HAVING COUNT(*) > 1
),
successful_purchases AS (
    SELECT COUNT(*) AS value
    FROM payments payment
    WHERE payment.order_id IN (SELECT id FROM sf048_orders)
      AND payment.status = 'SUCCEEDED'
),
sold_seats AS (
    SELECT COUNT(*) AS value
    FROM event_seats event_seat
    WHERE event_seat.id IN (SELECT id FROM sf048_event_seats)
      AND event_seat.permanent_status = 'SOLD'
),
issued_tickets AS (
    SELECT COUNT(*) AS value
    FROM tickets ticket
    WHERE ticket.event_seat_id IN (SELECT id FROM sf048_event_seats)
)
SELECT 'duplicate_sales' AS metric, COUNT(*)::text AS value
FROM duplicate_sales
UNION ALL
SELECT 'payment_duplicate_count', COUNT(*)::text
FROM duplicate_successful_payments
UNION ALL
SELECT 'successful_purchases', value::text
FROM successful_purchases
UNION ALL
SELECT 'sold_seats', value::text
FROM sold_seats
UNION ALL
SELECT 'issued_tickets', value::text
FROM issued_tickets
UNION ALL
SELECT
    'verification_passed',
    (
        (SELECT COUNT(*) FROM duplicate_sales) = 0
        AND (SELECT COUNT(*) FROM duplicate_successful_payments) = 0
    )::text;

WITH sf048_events AS (
    SELECT id
    FROM events
    WHERE name LIKE 'SF-048%'
),
duplicate_sales AS (
    SELECT ticket.event_seat_id, COUNT(*) AS ticket_count
    FROM tickets ticket
    JOIN event_seats event_seat ON event_seat.id = ticket.event_seat_id
    WHERE event_seat.event_id IN (SELECT id FROM sf048_events)
    GROUP BY ticket.event_seat_id
    HAVING COUNT(*) > 1
)
SELECT *
FROM duplicate_sales
ORDER BY ticket_count DESC, event_seat_id;
