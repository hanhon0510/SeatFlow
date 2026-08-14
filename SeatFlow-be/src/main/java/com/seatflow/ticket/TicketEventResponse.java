package com.seatflow.ticket;

import java.time.Instant;
import java.util.UUID;

public record TicketEventResponse(
		UUID id,
		String name,
		Instant startTime,
		UUID venueId,
		String venueName,
		String venueAddress,
		String venueCity,
		String venueCountry,
		String venueTimezone) {

	public static TicketEventResponse from(TicketDetailRecord ticket) {
		return new TicketEventResponse(
				ticket.eventId(),
				ticket.eventName(),
				ticket.eventStartTime(),
				ticket.venueId(),
				ticket.venueName(),
				ticket.venueAddress(),
				ticket.venueCity(),
				ticket.venueCountry(),
				ticket.venueTimezone());
	}
}
