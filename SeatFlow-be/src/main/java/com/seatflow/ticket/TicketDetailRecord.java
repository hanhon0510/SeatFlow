package com.seatflow.ticket;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TicketDetailRecord(
		UUID id,
		UUID orderId,
		UUID eventSeatId,
		String ticketCode,
		TicketStatus status,
		Instant issuedAt,
		Instant usedAt,
		Instant createdAt,
		UUID eventId,
		String eventName,
		Instant eventStartTime,
		UUID venueId,
		String venueName,
		String venueAddress,
		String venueCity,
		String venueCountry,
		String venueTimezone,
		UUID seatId,
		String sectionName,
		String rowLabel,
		int seatNumber,
		String seatLabel,
		boolean accessible,
		BigDecimal price) {
}
