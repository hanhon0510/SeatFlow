package com.seatflow.analytics;

/** What became of the checkouts (seat reservations awaiting payment) started in the range. */
public record AnalyticsCheckout(
		long started,
		long paid,
		long pending,
		long expired,
		long failed,
		long cancelled) {
}
