package com.seatflow.payment;

public record IdempotentPaymentResult(
		int responseStatus,
		PaymentResponse responseBody) {
}
