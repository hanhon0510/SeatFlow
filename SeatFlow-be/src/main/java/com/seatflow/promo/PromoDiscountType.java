package com.seatflow.promo;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum PromoDiscountType {

	PERCENT,
	FIXED;

	/**
	 * Discount this type gives on {@code subtotal}, in whole currency units and never more than the subtotal.
	 */
	public BigDecimal discountFor(BigDecimal value, BigDecimal subtotal) {
		BigDecimal discount = this == PERCENT
				? subtotal.multiply(value).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
				: value.setScale(0, RoundingMode.HALF_UP);
		return discount.min(subtotal).max(BigDecimal.ZERO);
	}
}
