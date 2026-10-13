package com.seatflow.promo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PromoCodeResponse(
		UUID id,
		String code,
		PromoDiscountType discountType,
		BigDecimal discountValue,
		UUID eventId,
		Instant expiresAt,
		Integer maxUses,
		long usedCount,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {

	public static PromoCodeResponse from(PromoCodeRecord promo) {
		return new PromoCodeResponse(
				promo.id(),
				promo.code(),
				promo.discountType(),
				promo.discountValue(),
				promo.eventId(),
				promo.expiresAt(),
				promo.maxUses(),
				promo.usedCount(),
				promo.active(),
				promo.createdAt(),
				promo.updatedAt());
	}
}
