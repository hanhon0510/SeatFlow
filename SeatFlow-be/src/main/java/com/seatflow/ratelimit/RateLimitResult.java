package com.seatflow.ratelimit;

import java.time.Duration;

public record RateLimitResult(
		boolean allowed,
		int limit,
		int remaining,
		Duration retryAfter,
		Duration resetAfter) {
}
