package com.seatflow.promo;

import java.math.BigDecimal;

/** What a code would take off a subtotal. Advisory: the order is the authority on the final amount. */
public record PromoCodeValidationResponse(
		String code,
		PromoDiscountType discountType,
		BigDecimal discountValue,
		BigDecimal discountAmount,
		BigDecimal total) {
}
