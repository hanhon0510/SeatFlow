package com.seatflow.notification;

import java.time.Instant;
import java.util.UUID;

/** What the email telling someone their waitlist seats are ready says. */
public record WaitlistOfferEmailDetails(
		UUID entryId,
		String email,
		UUID eventId,
		String eventName,
		Instant eventStartTime,
		String venueName,
		String venueAddress,
		String venueCity,
		String venueTimezone,
		int seatCount,
		Instant offerExpiresAt) {
}
