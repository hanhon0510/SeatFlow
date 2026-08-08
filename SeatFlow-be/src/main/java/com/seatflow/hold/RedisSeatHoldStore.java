package com.seatflow.hold;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisSeatHoldStore implements SeatHoldStore {

	private final StringRedisTemplate redisTemplate;

	public RedisSeatHoldStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public boolean tryAcquireSeat(UUID eventId, UUID eventSeatId, UUID holdId, Duration ttl) {
		Boolean acquired = redisTemplate.opsForValue()
				.setIfAbsent(SeatHoldRedisKeys.seat(eventId, eventSeatId), holdId.toString(), ttl);
		return Boolean.TRUE.equals(acquired);
	}

	@Override
	public void storeHold(SeatHoldRecord hold, Duration ttl) {
		redisTemplate.opsForValue().set(SeatHoldRedisKeys.data(hold.holdId()), payload(hold), ttl);
		redisTemplate.opsForValue().set(SeatHoldRedisKeys.user(hold.userId()), hold.holdId().toString(), ttl);
	}

	@Override
	public void releaseSeat(UUID eventId, UUID eventSeatId) {
		redisTemplate.delete(SeatHoldRedisKeys.seat(eventId, eventSeatId));
	}

	private static String payload(SeatHoldRecord hold) {
		return "holdId=%s;eventId=%s;eventSeatId=%s;seatId=%s;userId=%s;expiresAt=%s"
				.formatted(
						hold.holdId(),
						hold.eventId(),
						hold.eventSeatId(),
						hold.seatId(),
						hold.userId(),
						hold.expiresAt());
	}
}
