package com.seatflow.event;

public class EventPublicationException extends RuntimeException {

	public EventPublicationException() {
		super("Event publication failed");
	}
}
