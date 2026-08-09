package com.seatflow.reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationItemRecord(
		UUID id,
		UUID reservationId,
		UUID eventSeatId,
		BigDecimal price,
		Instant createdAt) {
}
