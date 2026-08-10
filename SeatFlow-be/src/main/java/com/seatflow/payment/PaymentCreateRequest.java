package com.seatflow.payment;

import jakarta.validation.constraints.NotBlank;

public record PaymentCreateRequest(
		@NotBlank String token) {

	@Override
	public String toString() {
		return "PaymentCreateRequest[token=[REDACTED]]";
	}
}
