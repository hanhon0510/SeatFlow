package com.seatflow.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRecord(
		UUID id,
		UUID orderId,
		PaymentStatus status,
		BigDecimal amount,
		String providerReference,
		String failureReason,
		Instant createdAt,
		Instant updatedAt) {
}
