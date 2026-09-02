package com.seatflow.event;

/** One seat row of one section, as the heatmap draws it: a single cell. */
public record EventSalesHeatmapRowResponse(
		String rowLabel,
		long seatsTotal,
		long seatsAvailable,
		long seatsSold,
		long seatsBlocked) {
}
