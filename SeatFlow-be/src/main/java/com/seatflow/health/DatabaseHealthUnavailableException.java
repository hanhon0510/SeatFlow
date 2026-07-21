package com.seatflow.health;

public class DatabaseHealthUnavailableException extends RuntimeException {

	public DatabaseHealthUnavailableException() {
		super("Database health check failed");
	}

	public DatabaseHealthUnavailableException(Throwable cause) {
		super("Database health check failed", cause);
	}

}

