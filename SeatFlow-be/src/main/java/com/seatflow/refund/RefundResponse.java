package com.seatflow.refund;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.seatflow.order.OrderStatus;

public record RefundResponse(
		UUID id,
		UUID orderId,
		OrderStatus orderStatus,
		BigDecimal amount,
		String currency,
		String reason,
		Instant refundedAt) {

	static RefundResponse from(RefundRecord refund) {
		return new RefundResponse(
				refund.id(),
				refund.orderId(),
				OrderStatus.REFUNDED,
				refund.amount(),
				refund.currency(),
				refund.reason(),
				refund.createdAt());
	}
}
