package com.seatflow.ticket;

import java.time.Instant;
import java.util.UUID;

public record TicketRecord(
		UUID id,
		UUID orderId,
		UUID eventSeatId,
		String ticketCode,
		TicketStatus status,
		Instant issuedAt,
		Instant usedAt,
		Instant createdAt) {
}
