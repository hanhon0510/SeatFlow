package com.seatflow.event;

public class EventStateConflictException extends RuntimeException {

	public EventStateConflictException() {
		super("Event state conflict");
	}
}
