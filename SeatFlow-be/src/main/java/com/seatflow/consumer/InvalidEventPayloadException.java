package com.seatflow.consumer;

public class InvalidEventPayloadException extends IllegalArgumentException {

	public InvalidEventPayloadException(String eventType) {
		super("Invalid payload for %s event".formatted(eventType));
	}

	public InvalidEventPayloadException(String eventType, Throwable cause) {
		super("Invalid payload for %s event".formatted(eventType), cause);
	}
}
