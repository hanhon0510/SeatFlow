package com.seatflow.event;

public class NoEventSeatsException extends RuntimeException {

	public NoEventSeatsException() {
		super("Event has no seats");
	}
}
