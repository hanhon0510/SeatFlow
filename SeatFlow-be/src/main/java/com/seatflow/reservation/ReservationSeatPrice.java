package com.seatflow.reservation;

import java.math.BigDecimal;
import java.util.UUID;

import com.seatflow.event.EventSeatStatus;

public record ReservationSeatPrice(
		UUID eventSeatId,
		UUID eventId,
		BigDecimal price,
		EventSeatStatus permanentStatus) {
}
