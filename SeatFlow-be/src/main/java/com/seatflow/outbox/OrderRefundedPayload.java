package com.seatflow.outbox;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderRefundedPayload(
		UUID orderId,
		UUID refundId,
		UUID paymentId,
		UUID userId,
		BigDecimal amount,
		String currency,
		List<UUID> eventSeatIds,
		Instant refundedAt) {
}
