package com.seatflow.event;

public class DuplicateEventSectionException extends RuntimeException {

	public DuplicateEventSectionException() {
		super("Duplicate event section");
	}
}
