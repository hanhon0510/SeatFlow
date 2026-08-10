package com.seatflow.order;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record OrderCreateRequest(
		@NotNull UUID reservationId) {
}
