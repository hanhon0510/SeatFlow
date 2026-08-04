package com.seatflow.event;

import java.util.List;

public record PublicEventPageResponse(
		List<PublicEventResponse> items,
		int page,
		int size,
		long totalItems,
		int totalPages) {

	public static PublicEventPageResponse from(
			List<PublicEventCatalogRecord> events,
			int page,
			int size,
			long totalItems) {
		int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
		return new PublicEventPageResponse(
				events.stream().map(PublicEventResponse::from).toList(),
				page,
				size,
				totalItems,
				totalPages);
	}
}
