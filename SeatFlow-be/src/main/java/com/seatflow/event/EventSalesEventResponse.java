package com.seatflow.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The event header of a sales report. {@code salesStatus} is null for an event that is not
 * published, because nothing can be bought for it whatever its sales window says.
 */
public record EventSalesEventResponse(
		UUID id,
		UUID venueId,
		String venueName,
		String venueTimezone,
		String name,
		String description,
		Instant startTime,
		Instant salesStartTime,
		Instant salesEndTime,
		EventStatus status,
		EventSalesStatus salesStatus) {
}
