package com.seatflow.event;

import java.util.List;
import java.util.UUID;

public record EventSeatLayoutResponse(
		UUID eventId,
		List<EventSeatLayoutSectionResponse> sections) {
}
