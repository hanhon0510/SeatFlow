package com.seatflow.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AnalyticsReport(
		LocalDate from,
		LocalDate to,
		UUID eventId,
		List<AnalyticsCurrencyTotal> totals,
		List<AnalyticsDailyPoint> daily,
		List<AnalyticsEventTotal> topEvents,
		AnalyticsCheckout checkout,
		AnalyticsWaitlist waitlist,
		Instant generatedAt) {

	public AnalyticsReport {
		totals = List.copyOf(totals);
		daily = List.copyOf(daily);
		topEvents = List.copyOf(topEvents);
	}
}
