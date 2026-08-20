package com.seatflow.ratelimit;

public record RateLimitExceededResponse(
		int limit,
		int remaining,
		long retryAfterSeconds,
		long resetAfterSeconds) {
}
