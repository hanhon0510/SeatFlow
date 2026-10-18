package com.seatflow.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled whether emails are sent over SMTP; when false they are only logged
 * @param from the sender shown to recipients
 * @param frontendBaseUrl where links in emails point, without a trailing slash
 */
@ConfigurationProperties(prefix = "seatflow.mail")
public record SeatFlowMailProperties(boolean enabled, String from, String frontendBaseUrl) {

	private static final String DEFAULT_FROM = "SeatFlow <no-reply@seatflow.local>";
	private static final String DEFAULT_FRONTEND_BASE_URL = "http://localhost:5173";

	public SeatFlowMailProperties {
		from = from == null || from.isBlank() ? DEFAULT_FROM : from.trim();
		String base = frontendBaseUrl == null || frontendBaseUrl.isBlank()
				? DEFAULT_FRONTEND_BASE_URL
				: frontendBaseUrl.trim();
		frontendBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}
}
