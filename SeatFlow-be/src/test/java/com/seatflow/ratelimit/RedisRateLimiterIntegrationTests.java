package com.seatflow.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.support.RedisTestContainerSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimiterIntegrationTests extends RedisTestContainerSupport {

	@Autowired
	private RedisRateLimiter rateLimiter;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@BeforeEach
	void cleanRedis() {
		redisTemplate.execute((RedisCallback<Void>) connection -> {
			connection.serverCommands().flushDb();
			return null;
		});
	}

	@Test
	void requestsBelowLimitAreAllowedAndExcessRequestIsRejected() {
		String key = key();

		RateLimitResult first = rateLimiter.consume(key, 2, Duration.ofSeconds(30));
		RateLimitResult second = rateLimiter.consume(key, 2, Duration.ofSeconds(30));
		RateLimitResult third = rateLimiter.consume(key, 2, Duration.ofSeconds(30));

		assertThat(first.allowed()).isTrue();
		assertThat(first.remaining()).isEqualTo(1);
		assertThat(second.allowed()).isTrue();
		assertThat(second.remaining()).isZero();
		assertThat(third.allowed()).isFalse();
		assertThat(third.remaining()).isZero();
		assertThat(third.retryAfter()).isPositive();
	}

	@Test
	void limitResetsAfterWindow() throws Exception {
		String key = key();

		assertThat(rateLimiter.consume(key, 1, Duration.ofMillis(100)).allowed()).isTrue();
		assertThat(rateLimiter.consume(key, 1, Duration.ofMillis(100)).allowed()).isFalse();

		TimeUnit.MILLISECONDS.sleep(180);

		assertThat(rateLimiter.consume(key, 1, Duration.ofMillis(100)).allowed()).isTrue();
	}

	@Test
	void concurrentRequestsCannotBypassLimit() throws Exception {
		String key = key();
		int requestCount = 24;
		int limit = 5;
		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch start = new CountDownLatch(1);
		ConcurrentLinkedQueue<RateLimitResult> results = new ConcurrentLinkedQueue<>();
		var executor = Executors.newFixedThreadPool(requestCount);

		for (int index = 0; index < requestCount; index++) {
			executor.submit(() -> {
				ready.countDown();
				try {
					start.await();
					results.add(rateLimiter.consume(key, limit, Duration.ofSeconds(30)));
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			});
		}

		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		executor.shutdown();
		assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

		assertThat(results).hasSize(requestCount);
		assertThat(results.stream().filter(RateLimitResult::allowed).count()).isEqualTo(limit);
		assertThat(results.stream().filter(result -> !result.allowed()).count()).isEqualTo(requestCount - limit);
	}

	private static String key() {
		return "seatflow:test:rate-limit:" + UUID.randomUUID();
	}
}
