package com.seatflow.promo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PromoCodeCreateRequest(
		@NotNull @Pattern(regexp = "^[A-Za-z0-9]{4,32}$") String code,
		@NotNull PromoDiscountType discountType,
		@NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal discountValue,
		UUID eventId,
		Instant expiresAt,
		@Min(1) Integer maxUses) {
}
