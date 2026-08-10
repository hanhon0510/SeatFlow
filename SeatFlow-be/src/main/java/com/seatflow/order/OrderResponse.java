package com.seatflow.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
		UUID id,
		UUID reservationId,
		UUID userId,
		OrderStatus status,
		BigDecimal totalAmount,
		String currency,
		Instant createdAt,
		Instant updatedAt) {

	public static OrderResponse from(OrderRecord order) {
		return new OrderResponse(
				order.id(),
				order.reservationId(),
				order.userId(),
				order.status(),
				order.totalAmount(),
				order.currency(),
				order.createdAt(),
				order.updatedAt());
	}
}
