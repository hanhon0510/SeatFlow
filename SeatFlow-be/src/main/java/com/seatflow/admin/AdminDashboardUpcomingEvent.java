package com.seatflow.admin;

import java.time.Instant;
import java.util.UUID;

import com.seatflow.event.EventStatus;

public record AdminDashboardUpcomingEvent(
		UUID eventId,
		String name,
		String venueName,
		Instant startTime,
		Instant salesEndTime,
		EventStatus status,
		long seatsTotal,
		long seatsSold) {
}
