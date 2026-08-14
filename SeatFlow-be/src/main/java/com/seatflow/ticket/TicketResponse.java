package com.seatflow.ticket;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
		UUID id,
		UUID orderId,
		UUID eventSeatId,
		String ticketCode,
		TicketStatus status,
		Instant issuedAt,
		Instant usedAt,
		Instant createdAt,
		TicketEventResponse event,
		TicketSeatResponse seat,
		String qrData) {

	public static TicketResponse from(TicketDetailRecord ticket) {
		return new TicketResponse(
				ticket.id(),
				ticket.orderId(),
				ticket.eventSeatId(),
				ticket.ticketCode(),
				ticket.status(),
				ticket.issuedAt(),
				ticket.usedAt(),
				ticket.createdAt(),
				TicketEventResponse.from(ticket),
				TicketSeatResponse.from(ticket),
				qrData(ticket.id(), ticket.ticketCode()));
	}

	private static String qrData(UUID ticketId, String ticketCode) {
		return "seatflow:ticket:%s:%s".formatted(ticketId, ticketCode);
	}
}
