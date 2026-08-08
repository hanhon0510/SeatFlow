package com.seatflow.hold;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seatflow.holds")
public record SeatHoldProperties(Duration ttl, Integer maxSeats) {

	private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
	private static final int DEFAULT_MAX_SEATS = 8;

	public SeatHoldProperties {
		if (ttl == null) {
			ttl = DEFAULT_TTL;
		}
		if (ttl.isZero() || ttl.isNegative()) {
			throw new IllegalStateException("Seat hold TTL must be positive");
		}
		if (maxSeats == null) {
			maxSeats = DEFAULT_MAX_SEATS;
		}
		if (maxSeats <= 0) {
			throw new IllegalStateException("Seat hold maximum seat count must be positive");
		}
	}
}
