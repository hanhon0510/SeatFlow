package com.seatflow.health;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

@Service
public class RedisHealthService {

	private static final String UP = "UP";
	private static final String PONG = "PONG";

	private final RedisConnectionFactory redisConnectionFactory;

	public RedisHealthService(RedisConnectionFactory redisConnectionFactory) {
		this.redisConnectionFactory = redisConnectionFactory;
	}

	public RedisHealthResponse checkRedis() {
		String pong;
		try (RedisConnection connection = redisConnectionFactory.getConnection()) {
			pong = connection.ping();
		}
		catch (RuntimeException ex) {
			throw new RedisHealthUnavailableException(ex);
		}

		if (!PONG.equalsIgnoreCase(pong)) {
			throw new RedisHealthUnavailableException();
		}

		return new RedisHealthResponse(UP, "Redis");
	}

	public record RedisHealthResponse(String status, String cache) {
	}
}
