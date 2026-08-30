package com.seatflow.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origins the browser-facing surfaces accept: CORS on the REST API and the STOMP handshake.
 *
 * <p>The default covers the Vite dev server only. A deployment that serves the frontend from
 * another host has to name it, which is the point - the previous wildcard let any page on any
 * origin open a socket and subscribe to live seat updates for any event.
 */
@ConfigurationProperties(prefix = "seatflow.web")
public record WebOriginProperties(List<String> allowedOrigins) {

	private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of("http://localhost:5173");

	public WebOriginProperties {
		allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
				? DEFAULT_ALLOWED_ORIGINS
				: List.copyOf(allowedOrigins);
		if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
			throw new IllegalStateException(
					"seatflow.web.allowed-origins must name exact origins; wildcards are not accepted");
		}
	}
}
