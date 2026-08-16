package com.seatflow.consumer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderPaidAnalyticsRecord(
		UUID id,
		UUID eventId,
		UUID orderId,
		UUID reservationId,
		UUID userId,
		UUID paymentId,
		BigDecimal totalAmount,
		String currency,
		int seatCount,
		Instant occurredAt,
		UUID correlationId,
		Instant createdAt) {
}
