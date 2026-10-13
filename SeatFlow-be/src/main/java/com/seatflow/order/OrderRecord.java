package com.seatflow.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderRecord(
		UUID id,
		UUID reservationId,
		UUID userId,
		OrderStatus status,
		BigDecimal totalAmount,
		String currency,
		Instant createdAt,
		Instant updatedAt,
		BigDecimal discountAmount,
		String promoCode) {

	public OrderRecord(
			UUID id,
			UUID reservationId,
			UUID userId,
			OrderStatus status,
			BigDecimal totalAmount,
			String currency,
			Instant createdAt,
			Instant updatedAt) {
		this(id, reservationId, userId, status, totalAmount, currency, createdAt, updatedAt, BigDecimal.ZERO, null);
	}
}
