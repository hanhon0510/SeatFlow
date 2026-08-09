package com.seatflow.event;

import java.math.BigDecimal;
import java.util.UUID;

public record EventSeatLayoutSeatResponse(
		UUID eventSeatId,
		String seatLabel,
		int seatNumber,
		BigDecimal price,
		EventSeatStatus permanentStatus,
		EventSeatLayoutStatus status,
		boolean accessible) {

	public static EventSeatLayoutSeatResponse from(EventSeatLayoutRow row, EventSeatLayoutStatus status) {
		return new EventSeatLayoutSeatResponse(
				row.eventSeatId(),
				row.seatLabel(),
				row.seatNumber(),
				row.price(),
				row.permanentStatus(),
				status,
				row.accessible());
	}
}
