package com.seatflow.health;

public class RedisHealthUnavailableException extends RuntimeException {

	public RedisHealthUnavailableException() {
		super("Redis health check failed");
	}

	public RedisHealthUnavailableException(Throwable cause) {
		super("Redis health check failed", cause);
	}
}
