package com.seatflow.analytics;

import java.math.BigDecimal;
import java.util.UUID;

public record AnalyticsEventTotal(
		UUID eventId,
		String eventName,
		String currency,
		long orders,
		long seats,
		BigDecimal gross,
		BigDecimal refunded) {

	public BigDecimal net() {
		return gross.subtract(refunded);
	}
}
