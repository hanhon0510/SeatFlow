package com.seatflow.health;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class RedisHealthServiceTests {

	@Test
	void redisFailuresProduceSafeErrors() {
		RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
		when(connectionFactory.getConnection()).thenThrow(new RuntimeException("internal redis detail"));
		RedisHealthService service = new RedisHealthService(connectionFactory);

		assertThatThrownBy(service::checkRedis)
				.isInstanceOf(RedisHealthUnavailableException.class)
				.hasMessage("Redis health check failed")
				.hasMessageNotContaining("internal redis detail");
	}

	@Test
	void nonPongResponsesAreUnavailable() {
		RedisConnection connection = mock(RedisConnection.class);
		RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
		when(connectionFactory.getConnection()).thenReturn(connection);
		when(connection.ping()).thenReturn("NOPE");
		RedisHealthService service = new RedisHealthService(connectionFactory);

		assertThatThrownBy(service::checkRedis)
				.isInstanceOf(RedisHealthUnavailableException.class)
				.hasMessage("Redis health check failed");
	}
}
