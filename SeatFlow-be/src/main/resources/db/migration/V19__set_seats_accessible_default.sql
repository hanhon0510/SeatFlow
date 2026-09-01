-- Seats are wheelchair accessible unless an admin marks them standard, so the column default
-- follows the product default. Existing rows keep the value they were created with.
ALTER TABLE seats
    ALTER COLUMN accessible SET DEFAULT TRUE;

COMMENT ON COLUMN seats.accessible IS
    'Wheelchair accessible seat. Presentation only: it marks the seat on the seat map and never affects availability, pricing, or holds.';
