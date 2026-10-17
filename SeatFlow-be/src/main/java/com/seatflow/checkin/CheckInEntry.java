package com.seatflow.checkin;

import java.time.Instant;
import java.util.UUID;

/** One admission in the recent-scans list. */
public record CheckInEntry(
		UUID ticketId,
		String sectionName,
		String rowLabel,
		String seatLabel,
		String buyerEmail,
		Instant checkedInAt) {
}
