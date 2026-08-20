package com.seatflow.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisRateLimiter {

	private static final RedisScript<String> CONSUME_SCRIPT = RedisScript.of("""
			local limit = tonumber(ARGV[1])
			local windowMillis = tonumber(ARGV[2])
			local current = redis.call('INCR', KEYS[1])

			if current == 1 then
				redis.call('PEXPIRE', KEYS[1], windowMillis)
			end

			local ttl = redis.call('PTTL', KEYS[1])
			if ttl < 0 then
				redis.call('PEXPIRE', KEYS[1], windowMillis)
				ttl = windowMillis
			end

			local allowed = 1
			if current > limit then
				allowed = 0
			end

			return tostring(allowed) .. ':' .. tostring(current) .. ':' .. tostring(ttl)
			""", String.class);

	private final StringRedisTemplate redisTemplate;

	public RedisRateLimiter(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public RateLimitResult consume(String key, int limit, Duration window) {
		try {
			String response = redisTemplate.execute(
					CONSUME_SCRIPT,
					List.of(key),
					Integer.toString(limit),
					Long.toString(window.toMillis()));
			return parseResponse(response, limit);
		}
		catch (RuntimeException ex) {
			throw new RateLimitStorageException(ex);
		}
	}

	private static RateLimitResult parseResponse(String response, int limit) {
		if (response == null) {
			throw new RateLimitStorageException(new IllegalStateException("Redis returned no rate limit response"));
		}

		String[] parts = response.split(":");
		if (parts.length != 3) {
			throw new RateLimitStorageException(new IllegalStateException("Invalid Redis rate limit response"));
		}

		boolean allowed = "1".equals(parts[0]);
		int count = Integer.parseInt(parts[1]);
		long ttlMillis = Long.parseLong(parts[2]);
		Duration resetAfter = Duration.ofMillis(Math.max(ttlMillis, 0));
		Duration retryAfter = allowed ? Duration.ZERO : resetAfter;
		return new RateLimitResult(
				allowed,
				limit,
				Math.max(limit - count, 0),
				retryAfter,
				resetAfter);
	}
}
