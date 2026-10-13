package com.seatflow.promo;

public class DuplicatePromoCodeException extends RuntimeException {

	public DuplicatePromoCodeException() {
		super("Promo code already exists");
	}
}
