package com.seatflow.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seatflow.rate-limit")
public record RateLimitProperties(
		Boolean enabled,
		Boolean trustProxyHeaders,
		List<String> trustedProxies,
		Policy login,
		Policy loginPerIp,
		Policy register,
		Policy holds,
		Policy payments,
		Policy seatLayout) {

	private static final boolean DEFAULT_ENABLED = true;
	private static final boolean DEFAULT_TRUST_PROXY_HEADERS = false;

	public RateLimitProperties {
		enabled = enabled == null ? DEFAULT_ENABLED : enabled;
		trustProxyHeaders = trustProxyHeaders == null ? DEFAULT_TRUST_PROXY_HEADERS : trustProxyHeaders;
		trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
		login = Policy.withDefaults(login, 5, Duration.ofMinutes(1), "login");
		loginPerIp = Policy.withDefaults(loginPerIp, 30, Duration.ofMinutes(15), "login-per-ip");
		register = Policy.withDefaults(register, 3, Duration.ofMinutes(10), "register");
		holds = Policy.withDefaults(holds, 20, Duration.ofMinutes(1), "holds");
		payments = Policy.withDefaults(payments, 10, Duration.ofMinutes(1), "payments");
		seatLayout = Policy.withDefaults(seatLayout, 60, Duration.ofMinutes(1), "seat-layout");
	}

	public boolean isEnabled() {
		return Boolean.TRUE.equals(enabled);
	}

	/**
	 * Forwarding headers are only honoured when the peer is itself a configured trusted proxy,
	 * so an empty {@code trustedProxies} list disables header parsing regardless of this flag.
	 * Without that pairing the header is attacker-controlled and every bucket becomes bypassable.
	 */
	public boolean trustProxyHeadersEnabled() {
		return Boolean.TRUE.equals(trustProxyHeaders) && !trustedProxies.isEmpty();
	}

	public record Policy(Integer limit, Duration window) {

		private static Policy withDefaults(Policy policy, int defaultLimit, Duration defaultWindow, String name) {
			Integer limit = policy == null ? null : policy.limit();
			Duration window = policy == null ? null : policy.window();
			if (limit == null) {
				limit = defaultLimit;
			}
			if (window == null) {
				window = defaultWindow;
			}
			if (limit <= 0) {
				throw new IllegalStateException("Rate limit for %s must be positive".formatted(name));
			}
			if (window.isZero() || window.isNegative()) {
				throw new IllegalStateException("Rate limit window for %s must be positive".formatted(name));
			}
			return new Policy(limit, window);
		}
	}
}
