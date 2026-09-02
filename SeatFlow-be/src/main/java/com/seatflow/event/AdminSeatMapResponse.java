package com.seatflow.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminSeatMapResponse(
		UUID eventId,
		List<EventSeatLayoutSectionResponse> sections,
		List<AdminSeatOrderResponse> orders,
		Instant generatedAt) {

	public AdminSeatMapResponse {
		sections = List.copyOf(sections);
		orders = List.copyOf(orders);
	}
}
