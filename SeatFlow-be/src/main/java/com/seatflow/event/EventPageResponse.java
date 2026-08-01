package com.seatflow.event;

import java.util.List;

public record EventPageResponse(
		List<EventResponse> items,
		int page,
		int size,
		long totalItems,
		int totalPages) {

	public static EventPageResponse from(List<EventRecord> events, int page, int size, long totalItems) {
		int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
		return new EventPageResponse(
				events.stream().map(EventResponse::from).toList(),
				page,
				size,
				totalItems,
				totalPages);
	}
}
