package com.seatflow.idempotency;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seatflow.idempotency")
public record IdempotencyProperties(Duration ttl) {

	private static final Duration DEFAULT_TTL = Duration.ofHours(24);

	public IdempotencyProperties {
		if (ttl == null) {
			ttl = DEFAULT_TTL;
		}
		if (ttl.isZero() || ttl.isNegative()) {
			throw new IllegalStateException("Idempotency TTL must be positive");
		}
	}
}
