package com.seatflow.refund;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundRecord(
		UUID id,
		UUID orderId,
		UUID paymentId,
		BigDecimal amount,
		String currency,
		String reason,
		UUID refundedBy,
		Instant createdAt) {
}
