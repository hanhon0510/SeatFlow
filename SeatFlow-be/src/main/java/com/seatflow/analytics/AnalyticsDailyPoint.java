package com.seatflow.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AnalyticsDailyPoint(
		LocalDate date,
		String currency,
		long orders,
		long seats,
		BigDecimal gross,
		BigDecimal refunded) {
}
