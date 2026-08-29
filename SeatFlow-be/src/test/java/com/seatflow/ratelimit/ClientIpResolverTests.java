package com.seatflow.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTests {

	@Test
	void ignoresProxyHeadersWhenForwardingIsDisabled() {
		MockHttpServletRequest request = request("10.0.0.10", "203.0.113.20");

		assertThat(resolver(false, List.of("10.0.0.0/8")).resolve(request)).isEqualTo("10.0.0.10");
	}

	@Test
	void ignoresProxyHeadersWhenNoTrustedProxyIsConfigured() {
		MockHttpServletRequest request = request("10.0.0.10", "203.0.113.20");

		// Enabling the flag alone must not be enough: with no trusted proxy list the header is
		// just attacker-supplied text.
		assertThat(resolver(true, List.of()).resolve(request)).isEqualTo("10.0.0.10");
	}

	@Test
	void ignoresProxyHeadersWhenThePeerIsNotATrustedProxy() {
		MockHttpServletRequest request = request("198.51.100.7", "203.0.113.20");

		assertThat(resolver(true, List.of("10.0.0.0/8")).resolve(request)).isEqualTo("198.51.100.7");
	}

	@Test
	void takesTheRightmostUntrustedHopSoASpoofedPrefixIsIgnored() {
		// The client claims two extra hops; only the entry our own proxy appended is credible.
		MockHttpServletRequest request = request("10.0.0.10", "1.1.1.1, 2.2.2.2, 203.0.113.20");

		assertThat(resolver(true, List.of("10.0.0.0/8")).resolve(request)).isEqualTo("203.0.113.20");
	}

	@Test
	void skipsChainedTrustedProxiesToReachTheClient() {
		MockHttpServletRequest request = request("10.0.0.10", "203.0.113.20, 10.0.0.11, 10.0.0.12");

		assertThat(resolver(true, List.of("10.0.0.0/8")).resolve(request)).isEqualTo("203.0.113.20");
	}

	@Test
	void fallsBackToPeerWhenEveryHopIsTrustedInfrastructure() {
		MockHttpServletRequest request = request("10.0.0.10", "10.0.0.11, 10.0.0.12");

		assertThat(resolver(true, List.of("10.0.0.0/8")).resolve(request)).isEqualTo("10.0.0.10");
	}

	@Test
	void readsForwardedHeaderWhenForwardedForIsAbsent() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.10");
		request.addHeader("Forwarded", "for=\"203.0.113.30\";proto=https");

		assertThat(resolver(true, List.of("10.0.0.0/8")).resolve(request)).isEqualTo("203.0.113.30");
	}

	@Test
	void stripsPortsFromForwardedEntries() {
		MockHttpServletRequest request = request("10.0.0.10", "203.0.113.20:41234");

		assertThat(resolver(true, List.of("10.0.0.0/8")).resolve(request)).isEqualTo("203.0.113.20");
	}

	private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddr);
		request.addHeader("X-Forwarded-For", forwardedFor);
		return request;
	}

	private static ClientIpResolver resolver(boolean trustProxyHeaders, List<String> trustedProxies) {
		return new ClientIpResolver(properties(trustProxyHeaders, trustedProxies));
	}

	private static RateLimitProperties properties(boolean trustProxyHeaders, List<String> trustedProxies) {
		RateLimitProperties.Policy policy = new RateLimitProperties.Policy(1, Duration.ofMinutes(1));
		return new RateLimitProperties(
				true,
				trustProxyHeaders,
				trustedProxies,
				policy,
				policy,
				policy,
				policy,
				policy,
				policy);
	}
}
