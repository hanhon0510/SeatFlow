package com.seatflow.waitlist;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param state NONE when the buyer is not on the list
 * @param position 1 for the next in line; only while WAITING
 * @param queueLength how many are waiting in total
 * @param offerExpiresAt when the offered seats go back on sale; only while OFFERED
 * @param eventSeatIds the seats set aside for the buyer; only while OFFERED
 * @param joinable whether the buyer could join right now: the event is on sale and has no free seats
 */
public record WaitlistStatusResponse(
		UUID eventId,
		WaitlistState state,
		UUID entryId,
		Integer seatCount,
		Integer position,
		long queueLength,
		Instant offerExpiresAt,
		List<UUID> eventSeatIds,
		boolean joinable) {
}
