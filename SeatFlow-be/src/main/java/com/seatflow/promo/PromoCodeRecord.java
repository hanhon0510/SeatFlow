package com.seatflow.promo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PromoCodeRecord(
		UUID id,
		String code,
		PromoDiscountType discountType,
		BigDecimal discountValue,
		UUID eventId,
		Instant expiresAt,
		Integer maxUses,
		boolean active,
		long usedCount,
		Instant createdAt,
		Instant updatedAt) {

	public boolean isExpired(Instant now) {
		return expiresAt != null && !expiresAt.isAfter(now);
	}

	public boolean isExhausted() {
		return maxUses != null && usedCount >= maxUses;
	}

	public BigDecimal discountFor(BigDecimal subtotal) {
		return discountType.discountFor(discountValue, subtotal);
	}
}
