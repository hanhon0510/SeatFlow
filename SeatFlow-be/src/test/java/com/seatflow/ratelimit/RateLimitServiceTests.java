package com.seatflow.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

class RateLimitServiceTests {

	private static final UUID USER_ID = UUID.fromString("c144397b-1b17-4a45-a1ef-b30ef84d5a79");
	private static final UUID OTHER_USER_ID = UUID.fromString("d3329056-39f7-457e-a27c-95b9d52dfdf6");
	private static final UUID ORDER_ID = UUID.fromString("dc79974b-adc6-42cf-b751-e71811f1812d");

	@Test
	void loginKeyUsesClientIpAndNormalizedEmail() {
		RedisRateLimiter limiter = limiter();
		RateLimitService service = service(limiter, "203.0.113.10");
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

		service.checkLogin(new MockHttpServletRequest(), " User@Example.COM ");
		service.checkLogin(new MockHttpServletRequest(), "user@example.com");

		org.mockito.Mockito.verify(limiter, org.mockito.Mockito.times(4))
				.consume(keyCaptor.capture(), eq(2), eq(Duration.ofMinutes(1)));
		List<String> keys = keyCaptor.getAllValues();
		assertThat(keys.get(1)).isEqualTo(keys.get(3));
	}

	@Test
	void loginSpreadAcrossAccountsStillSharesOneAddressBucket() {
		RedisRateLimiter limiter = limiter();
		RateLimitService service = service(limiter, "203.0.113.10");
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

		service.checkLogin(new MockHttpServletRequest(), "victim-one@example.com");
		service.checkLogin(new MockHttpServletRequest(), "victim-two@example.com");

		org.mockito.Mockito.verify(limiter, org.mockito.Mockito.times(4))
				.consume(keyCaptor.capture(), eq(2), eq(Duration.ofMinutes(1)));
		List<String> keys = keyCaptor.getAllValues();
		// Without the shared address bucket, every new account name would hand a sprayer a
		// fresh allowance and the throttle would never bite.
		assertThat(keys.get(0)).isEqualTo(keys.get(2));
		assertThat(keys.get(1)).isNotEqualTo(keys.get(3));
	}

	@Test
	void seatLayoutIsScopedByClientAddress() {
		RedisRateLimiter limiter = limiter();
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

		service(limiter, "203.0.113.10").checkSeatLayout(new MockHttpServletRequest());
		service(limiter, "203.0.113.11").checkSeatLayout(new MockHttpServletRequest());

		org.mockito.Mockito.verify(limiter, org.mockito.Mockito.times(2))
				.consume(keyCaptor.capture(), eq(2), eq(Duration.ofMinutes(1)));
		assertThat(keyCaptor.getAllValues().get(0)).isNotEqualTo(keyCaptor.getAllValues().get(1));
	}

	@Test
	void holdLimitsAreScopedByUser() {
		RedisRateLimiter limiter = limiter();
		RateLimitService service = service(limiter, "203.0.113.10");
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

		service.checkHold(USER_ID);
		service.checkHold(OTHER_USER_ID);

		org.mockito.Mockito.verify(limiter, org.mockito.Mockito.times(2))
				.consume(keyCaptor.capture(), eq(2), eq(Duration.ofMinutes(1)));
		assertThat(keyCaptor.getAllValues().get(0)).isNotEqualTo(keyCaptor.getAllValues().get(1));
	}

	@Test
	void paymentLimitsAreScopedByUserAndOrder() {
		RedisRateLimiter limiter = limiter();
		RateLimitService service = service(limiter, "203.0.113.10");
		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

		service.checkPayment(USER_ID, ORDER_ID);
		service.checkPayment(OTHER_USER_ID, ORDER_ID);

		org.mockito.Mockito.verify(limiter, org.mockito.Mockito.times(2))
				.consume(keyCaptor.capture(), eq(2), eq(Duration.ofMinutes(1)));
		assertThat(keyCaptor.getAllValues().get(0)).isNotEqualTo(keyCaptor.getAllValues().get(1));
	}

	@Test
	void disabledRateLimiterDoesNotTouchRedis() {
		RedisRateLimiter limiter = limiter();
		RateLimitProperties properties = new RateLimitProperties(
				false,
				false,
				List.of(),
				new RateLimitProperties.Policy(2, Duration.ofMinutes(1)),
				new RateLimitProperties.Policy(2, Duration.ofMinutes(1)),
				new RateLimitProperties.Policy(2, Duration.ofMinutes(1)),
				new RateLimitProperties.Policy(2, Duration.ofMinutes(1)),
				new RateLimitProperties.Policy(2, Duration.ofMinutes(1)),
				new RateLimitProperties.Policy(2, Duration.ofMinutes(1)));
		RateLimitService service = new RateLimitService(properties, limiter, clientIpResolver("203.0.113.10"));

		service.checkHold(USER_ID);

		org.mockito.Mockito.verifyNoInteractions(limiter);
	}

	private static RateLimitService service(RedisRateLimiter limiter, String clientIp) {
		return new RateLimitService(properties(), limiter, clientIpResolver(clientIp));
	}

	private static RedisRateLimiter limiter() {
		RedisRateLimiter limiter = org.mockito.Mockito.mock(RedisRateLimiter.class);
		when(limiter.consume(anyString(), anyInt(), eq(Duration.ofMinutes(1))))
				.thenReturn(new RateLimitResult(true, 2, 1, Duration.ZERO, Duration.ofMinutes(1)));
		return limiter;
	}

	private static ClientIpResolver clientIpResolver(String clientIp) {
		ClientIpResolver resolver = org.mockito.Mockito.mock(ClientIpResolver.class);
		when(resolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(clientIp);
		return resolver;
	}

	private static RateLimitProperties properties() {
		return new RateLimitProperties(
				true,
				false,
				List.of(),
				new RateLimitProperties.Policy(2, Duration.ofMinutes(1)),
				new RateLimitProperties.Policy(2, Duration.ofMinutes(1)),
				new RateLimitProperties.Policy(2, Duration.ofMinutes(1)),
				new RateLimitProperties.Policy(2, Duration.ofMinutes(1)),
				new RateLimitProperties.Policy(2, Duration.ofMinutes(1)),
				new RateLimitProperties.Policy(2, Duration.ofMinutes(1)));
	}
}
