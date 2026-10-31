package com.seatflow.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Orders sold (paid, or paid and later refunded) on one UTC day for one event and currency.
 * Everything else in the report is added up from these rows.
 */
public record AnalyticsSalesRow(
		LocalDate date,
		UUID eventId,
		String eventName,
		String currency,
		long orders,
		long seats,
		BigDecimal gross,
		BigDecimal refunded,
		long refundedOrders) {
}
