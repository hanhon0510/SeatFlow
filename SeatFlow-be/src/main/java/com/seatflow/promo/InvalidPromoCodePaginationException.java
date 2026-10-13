package com.seatflow.promo;

public class InvalidPromoCodePaginationException extends RuntimeException {

	public InvalidPromoCodePaginationException() {
		super("Invalid promo code pagination");
	}
}
