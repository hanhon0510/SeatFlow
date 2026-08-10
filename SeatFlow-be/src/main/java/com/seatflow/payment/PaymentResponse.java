package com.seatflow.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
		UUID id,
		UUID orderId,
		PaymentStatus status,
		BigDecimal amount,
		String providerReference,
		String failureReason,
		Instant createdAt,
		Instant updatedAt) {

	public static PaymentResponse from(PaymentRecord payment) {
		return new PaymentResponse(
				payment.id(),
				payment.orderId(),
				payment.status(),
				payment.amount(),
				payment.providerReference(),
				payment.failureReason(),
				payment.createdAt(),
				payment.updatedAt());
	}
}
