package com.seatflow.outbox;

public class OutboxPublishException extends RuntimeException {

	public OutboxPublishException() {
		super("Unable to publish outbox event");
	}

	public OutboxPublishException(Throwable cause) {
		super("Unable to publish outbox event", cause);
	}
}
