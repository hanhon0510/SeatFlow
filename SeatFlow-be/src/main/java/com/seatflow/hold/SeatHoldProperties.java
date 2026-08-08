package com.seatflow.hold;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seatflow.holds")
public record SeatHoldProperties(Duration ttl) {

	private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

	public SeatHoldProperties {
		if (ttl == null) {
			ttl = DEFAULT_TTL;
		}
		if (ttl.isZero() || ttl.isNegative()) {
			throw new IllegalStateException("Seat hold TTL must be positive");
		}
	}
}
