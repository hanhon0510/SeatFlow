package com.seatflow.ticket;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketSeatResponse(
		UUID id,
		String sectionName,
		String rowLabel,
		int seatNumber,
		String seatLabel,
		boolean accessible,
		BigDecimal price) {

	public static TicketSeatResponse from(TicketDetailRecord ticket) {
		return new TicketSeatResponse(
				ticket.seatId(),
				ticket.sectionName(),
				ticket.rowLabel(),
				ticket.seatNumber(),
				ticket.seatLabel(),
				ticket.accessible(),
				ticket.price());
	}
}
