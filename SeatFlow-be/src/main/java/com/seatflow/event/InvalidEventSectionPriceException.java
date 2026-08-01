package com.seatflow.event;

public class InvalidEventSectionPriceException extends RuntimeException {

	public InvalidEventSectionPriceException() {
		super("Invalid event section price");
	}
}
