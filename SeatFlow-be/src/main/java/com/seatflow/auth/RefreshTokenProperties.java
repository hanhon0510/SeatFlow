package com.seatflow.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seatflow.refresh-token")
public record RefreshTokenProperties(
		String cookieName,
		long expiresInSeconds,
		boolean cookieSecure,
		String sameSite) {
}
