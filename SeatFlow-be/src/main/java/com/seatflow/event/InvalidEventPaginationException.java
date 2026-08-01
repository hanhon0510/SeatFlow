package com.seatflow.event;

public class InvalidEventPaginationException extends RuntimeException {

	public InvalidEventPaginationException() {
		super("Invalid event pagination");
	}
}
