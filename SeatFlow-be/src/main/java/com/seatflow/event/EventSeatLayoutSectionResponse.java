package com.seatflow.event;

import java.util.List;
import java.util.UUID;

public record EventSeatLayoutSectionResponse(
		UUID id,
		String name,
		int displayOrder,
		List<EventSeatLayoutRowResponse> rows) {
}
