package com.seatflow.seating;

public class SeatNotFoundException extends RuntimeException {

	public SeatNotFoundException() {
		super("Seat not found");
	}
}
