package com.seatflow.checkin;

import java.util.List;
import java.util.UUID;

public record CheckInSummaryResponse(
		UUID eventId,
		String eventName,
		long valid,
		long checkedIn,
		long remaining,
		List<CheckInEntry> recent) {

	public CheckInSummaryResponse {
		recent = List.copyOf(recent);
	}
}
