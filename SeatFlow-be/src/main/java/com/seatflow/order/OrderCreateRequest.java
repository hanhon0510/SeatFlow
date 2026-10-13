package com.seatflow.order;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderCreateRequest(
		@NotNull UUID reservationId,
		@Size(max = 32) String promoCode) {

	public OrderCreateRequest(UUID reservationId) {
		this(reservationId, null);
	}

	public boolean hasPromoCode() {
		return promoCode != null && !promoCode.isBlank();
	}
}
