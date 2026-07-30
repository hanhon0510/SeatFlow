package com.seatflow.seating;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SeatLayoutSectionResponse(
		UUID id,
		String name,
		int displayOrder,
		Instant createdAt,
		List<SeatResponse> seats) {
}
