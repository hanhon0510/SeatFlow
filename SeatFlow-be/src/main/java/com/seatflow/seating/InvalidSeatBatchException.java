package com.seatflow.seating;

public class InvalidSeatBatchException extends RuntimeException {

	public InvalidSeatBatchException() {
		super("Invalid seat batch");
	}
}
