package com.seatflow.seating;

public class DuplicateSeatLabelException extends RuntimeException {

	public DuplicateSeatLabelException(Throwable cause) {
		super("Duplicate seat label", cause);
	}
}
