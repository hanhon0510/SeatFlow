package com.seatflow.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.seatflow.order.OrderStatus;

/** One order as read from the database, joined with its event, venue and refund. */
record OrderHistoryRow(
		UUID orderId,
		OrderStatus status,
		BigDecimal totalAmount,
		BigDecimal discountAmount,
		String promoCode,
		String currency,
		Instant createdAt,
		Instant updatedAt,
		UUID eventId,
		String eventName,
		Instant eventStartTime,
		String venueName,
		String venueTimezone,
		boolean eventCancelled,
		String cancellationReason,
		BigDecimal refundAmount,
		String refundReason,
		Instant refundedAt) {
}
