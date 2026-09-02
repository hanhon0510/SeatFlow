package com.seatflow.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.seatflow.order.OrderStatus;

public record EventSalesRecentOrderResponse(
		UUID orderId,
		String buyerEmail,
		OrderStatus status,
		BigDecimal totalAmount,
		String currency,
		long seatCount,
		Instant createdAt,
		Instant updatedAt) {
}
