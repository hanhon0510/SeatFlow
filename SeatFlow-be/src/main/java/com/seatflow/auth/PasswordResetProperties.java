package com.seatflow.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param ttl how long a reset link stays valid */
@ConfigurationProperties(prefix = "seatflow.password-reset")
public record PasswordResetProperties(Duration ttl) {

	private static final Duration DEFAULT_TTL = Duration.ofHours(1);

	public PasswordResetProperties {
		ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? DEFAULT_TTL : ttl;
	}
}
