package com.seatflow.venue;

public class InvalidVenuePaginationException extends RuntimeException {

	public InvalidVenuePaginationException() {
		super("Invalid venue pagination");
	}
}
