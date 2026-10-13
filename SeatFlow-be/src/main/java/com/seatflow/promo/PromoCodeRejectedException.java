package com.seatflow.promo;

/** A customer-supplied code that cannot be applied to this purchase. */
public class PromoCodeRejectedException extends RuntimeException {

	public enum Reason {
		UNKNOWN,
		INACTIVE,
		EXPIRED,
		EXHAUSTED,
		WRONG_EVENT
	}

	private final Reason reason;

	public PromoCodeRejectedException(Reason reason) {
		super("Promo code cannot be applied: " + reason);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}
}
