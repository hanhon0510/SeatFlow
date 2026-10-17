package com.seatflow.checkin;

import java.time.Instant;
import java.util.UUID;

import com.seatflow.ticket.TicketStatus;

/** A ticket as the door staff needs to see it, joined to its seat, event and buyer. */
public record CheckInTicket(
		UUID ticketId,
		TicketStatus status,
		Instant usedAt,
		UUID eventId,
		String sectionName,
		String rowLabel,
		String seatLabel,
		String buyerEmail) {
}
