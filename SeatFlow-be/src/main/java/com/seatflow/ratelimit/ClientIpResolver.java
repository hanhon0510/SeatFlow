package com.seatflow.ratelimit;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class ClientIpResolver {

	private final RateLimitProperties properties;

	public ClientIpResolver(RateLimitProperties properties) {
		this.properties = properties;
	}

	public String resolve(HttpServletRequest request) {
		if (properties.trustProxyHeadersEnabled()) {
			String forwardedFor = firstForwardedFor(request.getHeader("X-Forwarded-For"));
			if (StringUtils.hasText(forwardedFor)) {
				return forwardedFor;
			}
			String forwarded = forwardedFor(request.getHeader("Forwarded"));
			if (StringUtils.hasText(forwarded)) {
				return forwarded;
			}
		}
		return request.getRemoteAddr();
	}

	private static String firstForwardedFor(String header) {
		if (!StringUtils.hasText(header)) {
			return null;
		}
		return sanitize(header.split(",", 2)[0]);
	}

	private static String forwardedFor(String header) {
		if (!StringUtils.hasText(header)) {
			return null;
		}
		for (String part : header.split(";")) {
			String trimmed = part.trim();
			if (trimmed.regionMatches(true, 0, "for=", 0, 4)) {
				return sanitize(trimmed.substring(4));
			}
		}
		return null;
	}

	private static String sanitize(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String sanitized = value.trim();
		if (sanitized.startsWith("\"") && sanitized.endsWith("\"") && sanitized.length() > 1) {
			sanitized = sanitized.substring(1, sanitized.length() - 1);
		}
		if (sanitized.startsWith("[") && sanitized.contains("]")) {
			return sanitized.substring(1, sanitized.indexOf(']'));
		}
		int portIndex = sanitized.lastIndexOf(':');
		if (portIndex > 0 && sanitized.indexOf(':') == portIndex) {
			return sanitized.substring(0, portIndex);
		}
		return sanitized;
	}
}
