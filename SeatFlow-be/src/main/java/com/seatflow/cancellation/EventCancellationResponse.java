package com.seatflow.cancellation;

import java.time.Instant;
import java.util.UUID;

import com.seatflow.event.EventStatus;

/**
 * The state of a cancelled event's refunds. Refunding is done in batches, so a caller repeats the
 * request until {@code remainingRefunds} reaches zero; repeating it is always safe.
 */
public record EventCancellationResponse(
		UUID eventId,
		EventStatus status,
		Instant cancelledAt,
		String reason,
		long refundedOrders,
		long remainingRefunds,
		long ordersNeedingAttention) {

	static EventCancellationResponse of(EventCancellationRecord cancellation, CancellationRefundCounts counts) {
		return new EventCancellationResponse(
				cancellation.eventId(),
				EventStatus.CANCELLED,
				cancellation.cancelledAt(),
				cancellation.reason(),
				counts.refunded(),
				counts.remaining(),
				counts.needsAttention());
	}
}
