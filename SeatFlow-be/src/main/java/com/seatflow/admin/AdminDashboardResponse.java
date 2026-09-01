package com.seatflow.admin;

import java.time.Instant;
import java.util.List;

public record AdminDashboardResponse(
		AdminDashboardVenues venues,
		AdminDashboardEvents events,
		AdminDashboardSales sales,
		List<AdminDashboardUpcomingEvent> upcomingEvents,
		Instant generatedAt) {

	public AdminDashboardResponse {
		upcomingEvents = List.copyOf(upcomingEvents);
	}
}
