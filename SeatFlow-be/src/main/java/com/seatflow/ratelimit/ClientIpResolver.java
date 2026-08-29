package com.seatflow.ratelimit;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the address a rate limit bucket should be keyed on.
 *
 * <p>Forwarding headers are client-supplied, so they are only read when the immediate peer is
 * one of the configured trusted proxies. The chain is then walked from the right — the end
 * nearest to us, which our own infrastructure appended — and the first hop that is not itself a
 * trusted proxy is the client. Taking the leftmost entry instead would let anyone rotate a
 * spoofed {@code X-Forwarded-For} per request and opt out of rate limiting entirely.
 */
@Component
public class ClientIpResolver {

	private final RateLimitProperties properties;
	private final List<IpAddressMatcher> trustedProxyMatchers;

	public ClientIpResolver(RateLimitProperties properties) {
		this.properties = properties;
		this.trustedProxyMatchers = properties.trustedProxies().stream()
				.map(ClientIpResolver::matcher)
				.toList();
	}

	public String resolve(HttpServletRequest request) {
		String remoteAddr = request.getRemoteAddr();
		if (!properties.trustProxyHeadersEnabled() || !isTrustedProxy(remoteAddr)) {
			return remoteAddr;
		}
		return clientFromChain(forwardedChain(request), remoteAddr);
	}

	private String clientFromChain(List<String> chain, String remoteAddr) {
		List<String> hops = new ArrayList<>(chain);
		hops.add(remoteAddr);
		for (int index = hops.size() - 1; index >= 0; index--) {
			String hop = hops.get(index);
			if (StringUtils.hasText(hop) && !isTrustedProxy(hop)) {
				return hop;
			}
		}
		return remoteAddr;
	}

	private boolean isTrustedProxy(String address) {
		if (!StringUtils.hasText(address)) {
			return false;
		}
		for (IpAddressMatcher matcher : trustedProxyMatchers) {
			try {
				if (matcher.matches(address)) {
					return true;
				}
			}
			catch (IllegalArgumentException ex) {
				// Not an address this matcher can compare against (e.g. an obfuscated
				// RFC 7239 identifier); treat it as untrusted rather than failing the request.
			}
		}
		return false;
	}

	private static List<String> forwardedChain(HttpServletRequest request) {
		List<String> forwardedFor = splitForwardedFor(request.getHeader("X-Forwarded-For"));
		if (!forwardedFor.isEmpty()) {
			return forwardedFor;
		}
		return splitForwarded(request.getHeader("Forwarded"));
	}

	private static List<String> splitForwardedFor(String header) {
		if (!StringUtils.hasText(header)) {
			return List.of();
		}
		List<String> hops = new ArrayList<>();
		for (String part : header.split(",")) {
			String sanitized = sanitize(part);
			if (sanitized != null) {
				hops.add(sanitized);
			}
		}
		return hops;
	}

	private static List<String> splitForwarded(String header) {
		if (!StringUtils.hasText(header)) {
			return List.of();
		}
		List<String> hops = new ArrayList<>();
		for (String element : header.split(",")) {
			for (String directive : element.split(";")) {
				String trimmed = directive.trim();
				if (trimmed.regionMatches(true, 0, "for=", 0, 4)) {
					String sanitized = sanitize(trimmed.substring(4));
					if (sanitized != null) {
						hops.add(sanitized);
					}
				}
			}
		}
		return hops;
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
		return StringUtils.hasText(sanitized) ? sanitized : null;
	}

	private static IpAddressMatcher matcher(String trustedProxy) {
		try {
			return new IpAddressMatcher(trustedProxy);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException(
					"Invalid seatflow.rate-limit.trusted-proxies entry: " + trustedProxy, ex);
		}
	}
}
