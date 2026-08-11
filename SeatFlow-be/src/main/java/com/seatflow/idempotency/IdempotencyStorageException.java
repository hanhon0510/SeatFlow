package com.seatflow.idempotency;

public class IdempotencyStorageException extends RuntimeException {

	public IdempotencyStorageException(Throwable cause) {
		super(cause);
	}

	public IdempotencyStorageException() {
	}
}
