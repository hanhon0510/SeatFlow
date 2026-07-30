package com.seatflow.venue;

import java.util.List;

public record VenuePageResponse(
		List<VenueResponse> items,
		int page,
		int size,
		long totalItems,
		int totalPages) {

	public static VenuePageResponse from(List<VenueRecord> venues, int page, int size, long totalItems) {
		int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
		return new VenuePageResponse(
				venues.stream().map(VenueResponse::from).toList(),
				page,
				size,
				totalItems,
				totalPages);
	}
}
