package com.seatflow.analytics;

/** What became of the waitlist entries created in the range. */
public record AnalyticsWaitlist(
		long joined,
		long offered,
		long fulfilled,
		long expired,
		long cancelled) {
}
