package com.seatflow.outbox;

public class OutboxStorageException extends RuntimeException {

	public OutboxStorageException() {
		super("Unable to store outbox event");
	}

	public OutboxStorageException(Throwable cause) {
		super("Unable to store outbox event", cause);
	}
}
