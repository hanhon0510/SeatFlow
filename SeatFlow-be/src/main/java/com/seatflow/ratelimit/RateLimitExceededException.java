package com.seatflow.ratelimit;

public class RateLimitExceededException extends RuntimeException {

	private final RateLimitResult result;

	public RateLimitExceededException(RateLimitResult result) {
		super("Rate limit exceeded");
		this.result = result;
	}

	public RateLimitResult result() {
		return result;
	}
}
