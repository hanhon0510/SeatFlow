package com.seatflow.event;

public class MissingEventSectionPricingException extends RuntimeException {

	public MissingEventSectionPricingException() {
		super("Event section pricing is incomplete");
	}
}
