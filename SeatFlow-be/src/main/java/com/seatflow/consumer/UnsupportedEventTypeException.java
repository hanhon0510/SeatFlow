package com.seatflow.consumer;

public class UnsupportedEventTypeException extends IllegalArgumentException {

	public UnsupportedEventTypeException(String eventType, String expectedEventType) {
		super("Unsupported event type %s, expected %s".formatted(eventType, expectedEventType));
	}
}
