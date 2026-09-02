package com.seatflow.event;

import java.util.List;
import java.util.UUID;

public record EventSalesHeatmapSectionResponse(
		UUID sectionId,
		String name,
		List<EventSalesHeatmapRowResponse> rows) {

	public EventSalesHeatmapSectionResponse {
		rows = List.copyOf(rows);
	}
}
