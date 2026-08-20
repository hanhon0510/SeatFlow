package com.seatflow.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisRateLimiterTests {

	@Test
	void redisOutageFailsClosed() {
		StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
		when(redisTemplate.execute(any(RedisScript.class), anyList(), any(String.class), any(String.class)))
				.thenThrow(new RedisConnectionFailureException("Redis unavailable"));
		RedisRateLimiter rateLimiter = new RedisRateLimiter(redisTemplate);

		assertThatThrownBy(() -> rateLimiter.consume("seatflow:test:rate-limit", 1, Duration.ofSeconds(30)))
				.isInstanceOf(RateLimitStorageException.class)
				.hasMessage("Rate limit storage unavailable");
	}
}
