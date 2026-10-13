package com.seatflow.promo;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PromoCodeValidationRequest(
		@NotBlank @Size(max = 32) String code,
		@NotNull UUID eventId,
		@NotNull @DecimalMin("0") BigDecimal subtotal) {
}
