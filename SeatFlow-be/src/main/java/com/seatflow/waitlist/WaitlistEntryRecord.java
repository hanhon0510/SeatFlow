package com.seatflow.waitlist;

import java.time.Instant;
import java.util.UUID;

public record WaitlistEntryRecord(
		UUID id,
		UUID eventId,
		UUID userId,
		int seatCount,
		WaitlistStatus status,
		Instant createdAt,
		Instant offeredAt,
		Instant offerExpiresAt,
		Instant updatedAt) {

	public static WaitlistEntryRecord waiting(UUID id, UUID eventId, UUID userId, int seatCount, Instant now) {
		return new WaitlistEntryRecord(id, eventId, userId, seatCount, WaitlistStatus.WAITING, now, null, null, now);
	}
}
