package com.seatflow.promo;

public class PromoCodeNotFoundException extends RuntimeException {

	public PromoCodeNotFoundException() {
		super("Promo code not found");
	}
}
