package com.seatflow.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
class RedisSeatHoldStoreIntegrationTests extends RedisTestContainerSupport {

	private static final UUID EVENT_ID = UUID.fromString("5d2b80f6-4a0e-4f91-9676-e2c2d8b59e42");
	private static final UUID EVENT_SEAT_ID = UUID.fromString("0ab96cb6-0b8b-4db4-855e-1bd12f3fc0e5");
	private static final UUID EVENT_SEAT_ID_2 = UUID.fromString("252d8a6e-5dab-4ab2-bcb1-c4baeb017fd2");
	private static final UUID SEAT_ID = UUID.fromString("9eaf1782-8239-4c83-a2a0-9b622b468bf0");
	private static final UUID SEAT_ID_2 = UUID.fromString("86b8769e-080e-491f-9290-2eed6ef139e2");
	private static final UUID USER_ID = UUID.fromString("c144397b-1b17-4a45-a1ef-b30ef84d5a79");
	private static final UUID HOLD_ID = UUID.fromString("0ffae24a-c058-4e4e-8783-b556a70e097e");
	private static final Duration HOLD_EXPIRY = Duration.ofMinutes(5);

	@Autowired
	private RedisSeatHoldStore store;

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
	void createsAllSeatKeysAndHoldMetadataAtomically() {
		SeatHoldRecord hold = hold(HOLD_ID, USER_ID);

		assertThat(store.createHold(hold, Duration.ofSeconds(5))).isTrue();

		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID)))
				.isEqualTo(HOLD_ID.toString());
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID_2)))
				.isEqualTo(HOLD_ID.toString());
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.data(HOLD_ID)))
				.contains("eventSeatIds=%s,%s".formatted(EVENT_SEAT_ID, EVENT_SEAT_ID_2))
				.contains("seatIds=%s,%s".formatted(SEAT_ID, SEAT_ID_2));
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.user(USER_ID))).isEqualTo(HOLD_ID.toString());
		assertThat(redisTemplate.getExpire(SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID), TimeUnit.MILLISECONDS))
				.isPositive();
		assertThat(redisTemplate.getExpire(SeatHoldRedisKeys.data(HOLD_ID), TimeUnit.MILLISECONDS)).isPositive();
	}

	@Test
	void findsActiveHoldFromStoredMetadata() {
		SeatHoldRecord hold = hold(HOLD_ID, USER_ID);
		assertThat(store.createHold(hold, Duration.ofSeconds(5))).isTrue();

		assertThat(store.findHold(HOLD_ID))
				.hasValueSatisfying(retrieved -> {
					assertThat(retrieved.holdId()).isEqualTo(HOLD_ID);
					assertThat(retrieved.eventId()).isEqualTo(EVENT_ID);
					assertThat(retrieved.eventSeatIds()).containsExactly(EVENT_SEAT_ID, EVENT_SEAT_ID_2);
					assertThat(retrieved.seatIds()).containsExactly(SEAT_ID, SEAT_ID_2);
					assertThat(retrieved.userId()).isEqualTo(USER_ID);
				});
		assertThat(store.isHoldActive(hold)).isTrue();
	}

	@Test
	void findsActiveSeatHoldOwnersForSeatMapOverlay() {
		SeatHoldRecord hold = hold(HOLD_ID, USER_ID);
		assertThat(store.createHold(hold, Duration.ofSeconds(5))).isTrue();

		assertThat(store.findActiveSeatHoldOwners(EVENT_ID, List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2, UUID.randomUUID())))
				.containsOnly(
						org.assertj.core.api.Assertions.entry(EVENT_SEAT_ID, USER_ID),
						org.assertj.core.api.Assertions.entry(EVENT_SEAT_ID_2, USER_ID));
	}

	@Test
	void expiredHoldMetadataIsIgnoredForSeatMapOverlay() {
		SeatHoldRecord expiredHold = new SeatHoldRecord(
				HOLD_ID,
				EVENT_ID,
				List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2),
				List.of(SEAT_ID, SEAT_ID_2),
				USER_ID,
				Instant.now().minusSeconds(1));
		assertThat(store.createHold(expiredHold, Duration.ofSeconds(5))).isTrue();

		assertThat(store.findActiveSeatHoldOwners(EVENT_ID, List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2))).isEmpty();
	}

	@Test
	void existingSeatKeyRejectsWholeHoldWithoutPartialKeys() {
		UUID otherHoldId = UUID.fromString("712a54fa-0786-4471-8634-c0e01ad55c11");
		redisTemplate.opsForValue().set(
				SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID),
				otherHoldId.toString(),
				Duration.ofSeconds(5));

		assertThat(store.createHold(hold(HOLD_ID, USER_ID), Duration.ofSeconds(5))).isFalse();

		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID)))
				.isEqualTo(otherHoldId.toString());
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID_2))).isNull();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.data(HOLD_ID))).isNull();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.user(USER_ID))).isNull();
	}

	@Test
	void seatAndMetadataKeysExpireTogether() throws Exception {
		SeatHoldRecord hold = hold(HOLD_ID, USER_ID);

		assertThat(store.createHold(hold, Duration.ofMillis(300))).isTrue();

		for (int attempt = 0; attempt < 20 && anyHoldKeyExists(hold); attempt++) {
			Thread.sleep(100);
		}
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID))).isNull();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID_2))).isNull();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.data(HOLD_ID))).isNull();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.user(USER_ID))).isNull();
	}

	@Test
	void releaseHoldRemovesAllSeatAndMetadataKeysAtomically() {
		SeatHoldRecord hold = hold(HOLD_ID, USER_ID);
		assertThat(store.createHold(hold, Duration.ofSeconds(5))).isTrue();

		store.releaseHold(hold);

		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID))).isNull();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID_2))).isNull();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.data(HOLD_ID))).isNull();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.user(USER_ID))).isNull();
		assertThat(store.createHold(hold(UUID.randomUUID(), USER_ID), Duration.ofSeconds(5))).isTrue();
	}

	@Test
	void repeatedReleaseIsSafe() {
		SeatHoldRecord hold = hold(HOLD_ID, USER_ID);
		assertThat(store.createHold(hold, Duration.ofSeconds(5))).isTrue();

		store.releaseHold(hold);
		store.releaseHold(hold);

		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.data(HOLD_ID))).isNull();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID))).isNull();
	}

	@Test
	void releaseHandlesPartialDataAndDoesNotDeleteAnotherHoldSeatKey() {
		SeatHoldRecord hold = hold(HOLD_ID, USER_ID);
		UUID otherHoldId = UUID.fromString("712a54fa-0786-4471-8634-c0e01ad55c11");
		assertThat(store.createHold(hold, Duration.ofSeconds(5))).isTrue();
		redisTemplate.delete(SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID));
		redisTemplate.opsForValue().set(
				SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID_2),
				otherHoldId.toString(),
				Duration.ofSeconds(5));

		assertThat(store.isHoldActive(hold)).isFalse();

		store.releaseHold(hold);

		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.data(HOLD_ID))).isNull();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.user(USER_ID))).isNull();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID))).isNull();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(EVENT_ID, EVENT_SEAT_ID_2)))
				.isEqualTo(otherHoldId.toString());
	}

	private boolean anyHoldKeyExists(SeatHoldRecord hold) {
		return hold.eventSeatIds().stream()
				.map(eventSeatId -> SeatHoldRedisKeys.seat(EVENT_ID, eventSeatId))
				.anyMatch(key -> redisTemplate.opsForValue().get(key) != null)
				|| redisTemplate.opsForValue().get(SeatHoldRedisKeys.data(hold.holdId())) != null
				|| redisTemplate.opsForValue().get(SeatHoldRedisKeys.user(hold.userId())) != null;
	}

	private static SeatHoldRecord hold(UUID holdId, UUID userId) {
		return new SeatHoldRecord(
				holdId,
				EVENT_ID,
				List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2),
				List.of(SEAT_ID, SEAT_ID_2),
				userId,
				Instant.now().plus(HOLD_EXPIRY));
	}
}
