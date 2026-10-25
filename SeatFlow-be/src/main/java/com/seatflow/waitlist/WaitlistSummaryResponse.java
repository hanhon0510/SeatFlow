package com.seatflow.waitlist;

import java.util.UUID;

/** What staff see for an event: how much demand is queued behind a sold-out event, and how it went. */
public record WaitlistSummaryResponse(
		UUID eventId,
		long waiting,
		long waitingSeats,
		long offered,
		long fulfilled,
		long expired,
		long cancelled) {
}
