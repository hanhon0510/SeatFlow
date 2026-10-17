package com.seatflow.checkin;

import java.time.Instant;
import java.util.UUID;

/**
 * The verdict of one scan. The ticket details are present whenever the ticket belongs to the event
 * being checked in; for a ticket of another event or an unknown code there is nothing to show.
 */
public record CheckInResponse(
		CheckInOutcome outcome,
		UUID ticketId,
		String sectionName,
		String rowLabel,
		String seatLabel,
		String buyerEmail,
		Instant usedAt) {

	static CheckInResponse withoutTicket(CheckInOutcome outcome) {
		return new CheckInResponse(outcome, null, null, null, null, null, null);
	}

	static CheckInResponse of(CheckInOutcome outcome, CheckInTicket ticket, Instant usedAt) {
		return new CheckInResponse(
				outcome,
				ticket.ticketId(),
				ticket.sectionName(),
				ticket.rowLabel(),
				ticket.seatLabel(),
				ticket.buyerEmail(),
				usedAt);
	}
}
