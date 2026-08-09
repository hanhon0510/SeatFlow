package com.seatflow.hold;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

	private static final RedisScript<Long> RELEASE_HOLD_SCRIPT = RedisScript.of("""
			local holdId = ARGV[1]

			for index = 3, #KEYS do
				if redis.call('GET', KEYS[index]) == holdId then
					redis.call('DEL', KEYS[index])
				end
			end
			if redis.call('GET', KEYS[2]) == holdId then
				redis.call('DEL', KEYS[2])
			end
			redis.call('DEL', KEYS[1])
			return 1
			""", Long.class);

	private final StringRedisTemplate redisTemplate;
	private final Clock clock;

	public RedisSeatHoldStore(StringRedisTemplate redisTemplate, Clock clock) {
		this.redisTemplate = redisTemplate;
		this.clock = clock;
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

	@Override
	public Optional<SeatHoldRecord> findHold(UUID holdId) {
		String payload = redisTemplate.opsForValue().get(SeatHoldRedisKeys.data(holdId));
		if (payload == null) {
			return Optional.empty();
		}
		return parsePayload(payload);
	}

	@Override
	public boolean isHoldActive(SeatHoldRecord hold) {
		return hold.expiresAt().isAfter(clock.instant())
				&& hold.eventSeatIds().stream()
				.allMatch(eventSeatId -> hold.holdId().toString()
						.equals(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(hold.eventId(), eventSeatId))));
	}

	@Override
	public void releaseHold(SeatHoldRecord hold) {
		List<String> keys = new ArrayList<>();
		keys.add(SeatHoldRedisKeys.data(hold.holdId()));
		keys.add(SeatHoldRedisKeys.user(hold.userId()));
		hold.eventSeatIds().forEach(eventSeatId -> keys.add(SeatHoldRedisKeys.seat(hold.eventId(), eventSeatId)));

		redisTemplate.execute(RELEASE_HOLD_SCRIPT, keys, hold.holdId().toString());
	}

	@Override
	public Map<UUID, UUID> findActiveSeatHoldOwners(UUID eventId, List<UUID> eventSeatIds) {
		List<UUID> uniqueEventSeatIds = eventSeatIds.stream()
				.distinct()
				.toList();
		if (uniqueEventSeatIds.isEmpty()) {
			return Map.of();
		}

		List<String> seatKeys = uniqueEventSeatIds.stream()
				.map(eventSeatId -> SeatHoldRedisKeys.seat(eventId, eventSeatId))
				.toList();
		List<String> holdIdValues = redisTemplate.opsForValue().multiGet(seatKeys);
		if (holdIdValues == null) {
			return Map.of();
		}

		Map<UUID, List<UUID>> eventSeatIdsByHoldId = new LinkedHashMap<>();
		for (int index = 0; index < uniqueEventSeatIds.size(); index++) {
			UUID holdId = parseUuid(holdIdValues.get(index));
			if (holdId != null) {
				eventSeatIdsByHoldId.computeIfAbsent(holdId, ignored -> new ArrayList<>())
						.add(uniqueEventSeatIds.get(index));
			}
		}
		if (eventSeatIdsByHoldId.isEmpty()) {
			return Map.of();
		}

		List<UUID> holdIds = new ArrayList<>(eventSeatIdsByHoldId.keySet());
		List<String> holdDataKeys = holdIds.stream()
				.map(SeatHoldRedisKeys::data)
				.toList();
		List<String> holdPayloads = redisTemplate.opsForValue().multiGet(holdDataKeys);
		if (holdPayloads == null) {
			return Map.of();
		}

		Map<UUID, UUID> ownersByEventSeatId = new HashMap<>();
		for (int index = 0; index < holdIds.size(); index++) {
			UUID holdId = holdIds.get(index);
			parsePayload(holdPayloads.get(index))
					.filter(hold -> hold.holdId().equals(holdId))
					.filter(hold -> hold.eventId().equals(eventId))
					.filter(hold -> hold.expiresAt().isAfter(clock.instant()))
					.ifPresent(hold -> addHeldSeats(
							ownersByEventSeatId,
							eventSeatIdsByHoldId.getOrDefault(hold.holdId(), List.of()),
							hold));
		}
		return ownersByEventSeatId;
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

	private static Optional<SeatHoldRecord> parsePayload(String payload) {
		try {
			Map<String, String> fields = new HashMap<>();
			for (String part : payload.split(";")) {
				String[] keyValue = part.split("=", 2);
				if (keyValue.length == 2) {
					fields.put(keyValue[0], keyValue[1]);
				}
			}

			List<UUID> eventSeatIds = parseIds(fields.getOrDefault("eventSeatIds", fields.get("eventSeatId")));
			List<UUID> seatIds = parseIds(fields.getOrDefault("seatIds", fields.get("seatId")));
			if (eventSeatIds.isEmpty() || eventSeatIds.size() != seatIds.size()) {
				return Optional.empty();
			}

			return Optional.of(new SeatHoldRecord(
					UUID.fromString(fields.get("holdId")),
					UUID.fromString(fields.get("eventId")),
					eventSeatIds,
					seatIds,
					UUID.fromString(fields.get("userId")),
					Instant.parse(fields.get("expiresAt"))));
		}
		catch (RuntimeException ex) {
			return Optional.empty();
		}
	}

	private static List<UUID> parseIds(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return Arrays.stream(value.split(","))
				.map(UUID::fromString)
				.toList();
	}

	private static UUID parseUuid(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static void addHeldSeats(
			Map<UUID, UUID> ownersByEventSeatId,
			List<UUID> eventSeatIds,
			SeatHoldRecord hold) {
		Set<UUID> immutableSeatIds = new HashSet<>(hold.eventSeatIds());
		for (UUID eventSeatId : eventSeatIds) {
			if (immutableSeatIds.contains(eventSeatId)) {
				ownersByEventSeatId.put(eventSeatId, hold.userId());
			}
		}
	}
}
