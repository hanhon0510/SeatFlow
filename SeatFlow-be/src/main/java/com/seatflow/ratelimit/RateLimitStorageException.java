package com.seatflow.ratelimit;

public class RateLimitStorageException extends RuntimeException {

	public RateLimitStorageException(Throwable cause) {
		super("Rate limit storage unavailable", cause);
	}
}
