package com.seatflow.ticket;

import java.time.Instant;
import java.util.UUID;

/** One hand-over of a ticket, as staff see it. */
public record TicketTransferRecord(
		UUID id,
		UUID ticketId,
		String sectionName,
		String rowLabel,
		String seatLabel,
		String fromEmail,
		String toEmail,
		Instant transferredAt) {
}
