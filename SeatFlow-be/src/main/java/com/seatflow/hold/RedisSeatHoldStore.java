package com.seatflow.hold;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisSeatHoldStore implements SeatHoldStore {

	private static final RedisScript<Long> CREATE_HOLD_SCRIPT = RedisScript.of("""
			local ttl = tonumber(ARGV[1])
			local holdId = ARGV[2]
			local holdData = ARGV[3]
			local userHoldId = ARGV[4]
			local seatCount = #KEYS - 2

			for index = 1, seatCount do
				if redis.call('EXISTS', KEYS[index]) == 1 then
					return 0
				end
			end

			for index = 1, seatCount do
				redis.call('SET', KEYS[index], holdId, 'PX', ttl)
			end
			redis.call('SET', KEYS[seatCount + 1], holdData, 'PX', ttl)
			redis.call('SET', KEYS[seatCount + 2], userHoldId, 'PX', ttl)
			return 1
			""", Long.class);

	private final StringRedisTemplate redisTemplate;

	public RedisSeatHoldStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public boolean createHold(SeatHoldRecord hold, Duration ttl) {
		List<String> keys = new ArrayList<>();
		hold.eventSeatIds().forEach(eventSeatId -> keys.add(SeatHoldRedisKeys.seat(hold.eventId(), eventSeatId)));
		keys.add(SeatHoldRedisKeys.data(hold.holdId()));
		keys.add(SeatHoldRedisKeys.user(hold.userId()));

		Long result = redisTemplate.execute(
				CREATE_HOLD_SCRIPT,
				keys,
				Long.toString(ttl.toMillis()),
				hold.holdId().toString(),
				payload(hold),
				hold.holdId().toString());
		return Long.valueOf(1L).equals(result);
	}

	private static String payload(SeatHoldRecord hold) {
		return "holdId=%s;eventId=%s;eventSeatIds=%s;seatIds=%s;userId=%s;expiresAt=%s"
				.formatted(
						hold.holdId(),
						hold.eventId(),
						joinIds(hold.eventSeatIds()),
						joinIds(hold.seatIds()),
						hold.userId(),
						hold.expiresAt());
	}

	private static String joinIds(List<?> ids) {
		return ids.stream()
				.map(Object::toString)
				.collect(Collectors.joining(","));
	}
}
