package com.seatflow.outbox;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderPaidPayload(
		UUID orderId,
		UUID reservationId,
		UUID userId,
		UUID paymentId,
		BigDecimal totalAmount,
		String currency,
		List<UUID> eventSeatIds,
		Instant paidAt) {
}
