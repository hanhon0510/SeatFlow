package com.seatflow.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTests {

	@Test
	void ignoresProxyHeadersUnlessExplicitlyTrusted() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.10");
		request.addHeader("X-Forwarded-For", "203.0.113.20");
		ClientIpResolver resolver = new ClientIpResolver(properties(false));

		assertThat(resolver.resolve(request)).isEqualTo("10.0.0.10");
	}

	@Test
	void usesFirstForwardedAddressWhenProxyHeadersAreTrusted() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.10");
		request.addHeader("X-Forwarded-For", "203.0.113.20, 203.0.113.21");
		ClientIpResolver resolver = new ClientIpResolver(properties(true));

		assertThat(resolver.resolve(request)).isEqualTo("203.0.113.20");
	}

	@Test
	void fallsBackToForwardedHeaderWhenTrusted() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.10");
		request.addHeader("Forwarded", "for=\"203.0.113.30\";proto=https");
		ClientIpResolver resolver = new ClientIpResolver(properties(true));

		assertThat(resolver.resolve(request)).isEqualTo("203.0.113.30");
	}

	private static RateLimitProperties properties(boolean trustProxyHeaders) {
		return new RateLimitProperties(
				true,
				trustProxyHeaders,
				new RateLimitProperties.Policy(1, Duration.ofMinutes(1)),
				new RateLimitProperties.Policy(1, Duration.ofMinutes(1)),
				new RateLimitProperties.Policy(1, Duration.ofMinutes(1)),
				new RateLimitProperties.Policy(1, Duration.ofMinutes(1)));
	}
}
