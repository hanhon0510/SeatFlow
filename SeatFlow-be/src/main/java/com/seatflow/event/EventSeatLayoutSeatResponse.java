package com.seatflow.event;

import java.math.BigDecimal;
import java.util.UUID;

public record EventSeatLayoutSeatResponse(
		UUID eventSeatId,
		String seatLabel,
		int seatNumber,
		BigDecimal price,
		EventSeatStatus permanentStatus,
		boolean accessible) {

	public static EventSeatLayoutSeatResponse from(EventSeatLayoutRow row) {
		return new EventSeatLayoutSeatResponse(
				row.eventSeatId(),
				row.seatLabel(),
				row.seatNumber(),
				row.price(),
				row.permanentStatus(),
				row.accessible());
	}
}
