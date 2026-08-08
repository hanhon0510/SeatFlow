package com.seatflow.hold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.seatflow.event.EventSeatHoldCandidate;
import com.seatflow.event.EventSeatLayoutRow;
import com.seatflow.event.EventSeatMapper;
import com.seatflow.event.EventSeatRecord;
import com.seatflow.event.EventSeatStatus;
import com.seatflow.event.EventStatus;

class SeatHoldServiceTests {

	private static final UUID EVENT_ID = UUID.fromString("5d2b80f6-4a0e-4f91-9676-e2c2d8b59e42");
	private static final UUID OTHER_EVENT_ID = UUID.fromString("d7c03613-dcad-4b06-9df3-54c3ea256371");
	private static final UUID EVENT_SEAT_ID = UUID.fromString("0ab96cb6-0b8b-4db4-855e-1bd12f3fc0e5");
	private static final UUID SEAT_ID = UUID.fromString("9eaf1782-8239-4c83-a2a0-9b622b468bf0");
	private static final UUID USER_ID = UUID.fromString("c144397b-1b17-4a45-a1ef-b30ef84d5a79");
	private static final UUID OTHER_USER_ID = UUID.fromString("d3329056-39f7-457e-a27c-95b9d52dfdf6");
	private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
	private static final Duration TTL = Duration.ofMinutes(2);

	@Test
	void createsHoldForAvailableSeatDuringOpenSales() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(candidate(EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE), store, clock);

		SeatHoldResponse response = service.createHold(EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID));

		assertThat(response.holdId()).isNotNull();
		assertThat(response.eventId()).isEqualTo(EVENT_ID);
		assertThat(response.eventSeatId()).isEqualTo(EVENT_SEAT_ID);
		assertThat(response.userId()).isEqualTo(USER_ID);
		assertThat(response.expiresAt()).isEqualTo(NOW.plus(TTL));
		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isTrue();
		assertThat(store.dataHolds).containsKey(response.holdId());
		assertThat(store.userHolds).containsEntry(USER_ID, response.holdId());
	}

	@Test
	void rejectsCompetingHoldForSameSeat() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(candidate(EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE), store, clock);

		service.createHold(EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID));

		assertThatThrownBy(() -> service.createHold(EVENT_ID, OTHER_USER_ID, new SeatHoldRequest(EVENT_SEAT_ID)))
				.isInstanceOf(SeatHoldConflictException.class);
	}

	@Test
	void expiredHoldCanBeAcquiredAgain() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(candidate(EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE), store, clock);

		service.createHold(EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID));
		clock.advance(TTL.plusMillis(1));
		SeatHoldResponse secondHold = service.createHold(EVENT_ID, OTHER_USER_ID, new SeatHoldRequest(EVENT_SEAT_ID));

		assertThat(secondHold.userId()).isEqualTo(OTHER_USER_ID);
		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isTrue();
	}

	@Test
	void rejectsSeatThatDoesNotBelongToEvent() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(candidate(EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE), store, clock);

		assertThatThrownBy(() -> service.createHold(OTHER_EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID)))
				.isInstanceOf(SeatHoldConflictException.class);
		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isFalse();
	}

	@Test
	void rejectsDraftEvent() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(candidate(EventStatus.DRAFT, EventSeatStatus.AVAILABLE), store, clock);

		assertThatThrownBy(() -> service.createHold(EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID)))
				.isInstanceOf(SeatHoldConflictException.class);
	}

	@Test
	void rejectsSoldSeat() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(candidate(EventStatus.PUBLISHED, EventSeatStatus.SOLD), store, clock);

		assertThatThrownBy(() -> service.createHold(EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID)))
				.isInstanceOf(SeatHoldConflictException.class);
		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isFalse();
	}

	@Test
	void rejectsBlockedSeat() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(candidate(EventStatus.PUBLISHED, EventSeatStatus.BLOCKED), store, clock);

		assertThatThrownBy(() -> service.createHold(EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID)))
				.isInstanceOf(SeatHoldConflictException.class);
		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isFalse();
	}

	@Test
	void rejectsClosedSales() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		EventSeatHoldCandidate candidate = candidate(
				EventStatus.PUBLISHED,
				EventSeatStatus.AVAILABLE,
				NOW.minus(Duration.ofHours(3)),
				NOW.minus(Duration.ofHours(1)));
		SeatHoldService service = service(candidate, store, clock);

		assertThatThrownBy(() -> service.createHold(EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID)))
				.isInstanceOf(SeatHoldConflictException.class);
		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isFalse();
	}

	@Test
	void onlyOneConcurrentRequestAcquiresSeat() throws Exception {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(candidate(EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE), store, clock);
		int requestCount = 12;
		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(requestCount);
		AtomicBoolean failed = new AtomicBoolean(false);
		ConcurrentMap<UUID, Boolean> successfulUsers = new ConcurrentHashMap<>();

		for (int index = 0; index < requestCount; index++) {
			UUID userId = UUID.randomUUID();
			executor.submit(() -> {
				ready.countDown();
				try {
					start.await();
					service.createHold(EVENT_ID, userId, new SeatHoldRequest(EVENT_SEAT_ID));
					successfulUsers.put(userId, true);
				}
				catch (SeatHoldConflictException ignored) {
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					failed.set(true);
				}
				catch (RuntimeException ex) {
					failed.set(true);
				}
			});
		}

		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		executor.shutdown();
		assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

		assertThat(failed).isFalse();
		assertThat(successfulUsers).hasSize(1);
		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isTrue();
	}

	private static SeatHoldService service(
			EventSeatHoldCandidate candidate,
			FakeSeatHoldStore store,
			Clock clock) {
		return new SeatHoldService(
				new StubEventSeatMapper(candidate),
				store,
				new SeatHoldProperties(TTL),
				clock);
	}

	private static EventSeatHoldCandidate candidate(EventStatus eventStatus, EventSeatStatus permanentStatus) {
		return candidate(
				eventStatus,
				permanentStatus,
				NOW.minus(Duration.ofHours(1)),
				NOW.plus(Duration.ofHours(1)));
	}

	private static EventSeatHoldCandidate candidate(
			EventStatus eventStatus,
			EventSeatStatus permanentStatus,
			Instant salesStart,
			Instant salesEnd) {
		return new EventSeatHoldCandidate(
				EVENT_ID,
				EVENT_SEAT_ID,
				SEAT_ID,
				eventStatus,
				salesStart,
				salesEnd,
				permanentStatus);
	}

	private static final class StubEventSeatMapper implements EventSeatMapper {

		private final EventSeatHoldCandidate candidate;

		private StubEventSeatMapper(EventSeatHoldCandidate candidate) {
			this.candidate = candidate;
		}

		@Override
		public int insertForDraftEvent(UUID eventId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long countByEventId(UUID eventId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long countSourceSeatsForEvent(UUID eventId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long countMissingPricedSeatsForEvent(UUID eventId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<EventSeatRecord> findByEventId(UUID eventId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<EventSeatLayoutRow> findPublishedLayoutByEventId(UUID eventId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public EventSeatHoldCandidate findHoldCandidate(UUID eventId, UUID eventSeatId) {
			if (candidate == null) {
				return null;
			}
			if (!candidate.eventId().equals(eventId) || !candidate.eventSeatId().equals(eventSeatId)) {
				return null;
			}
			return candidate;
		}
	}

	private static final class FakeSeatHoldStore implements SeatHoldStore {

		private final Clock clock;
		private final ConcurrentMap<String, SeatEntry> seatHolds = new ConcurrentHashMap<>();
		private final ConcurrentMap<UUID, SeatHoldRecord> dataHolds = new ConcurrentHashMap<>();
		private final ConcurrentMap<UUID, UUID> userHolds = new ConcurrentHashMap<>();

		private FakeSeatHoldStore(Clock clock) {
			this.clock = clock;
		}

		@Override
		public boolean tryAcquireSeat(UUID eventId, UUID eventSeatId, UUID holdId, Duration ttl) {
			String key = SeatHoldRedisKeys.seat(eventId, eventSeatId);
			Instant now = clock.instant();
			AtomicBoolean acquired = new AtomicBoolean(false);
			seatHolds.compute(key, (ignored, existing) -> {
				if (existing == null || !existing.expiresAt().isAfter(now)) {
					acquired.set(true);
					return new SeatEntry(holdId, now.plus(ttl));
				}
				return existing;
			});
			return acquired.get();
		}

		@Override
		public void storeHold(SeatHoldRecord hold, Duration ttl) {
			dataHolds.put(hold.holdId(), hold);
			userHolds.put(hold.userId(), hold.holdId());
		}

		@Override
		public void releaseSeat(UUID eventId, UUID eventSeatId) {
			seatHolds.remove(SeatHoldRedisKeys.seat(eventId, eventSeatId));
		}

		private boolean hasSeatHold(UUID eventId, UUID eventSeatId) {
			SeatEntry entry = seatHolds.get(SeatHoldRedisKeys.seat(eventId, eventSeatId));
			return entry != null && entry.expiresAt().isAfter(clock.instant());
		}

		private record SeatEntry(UUID holdId, Instant expiresAt) {
		}
	}

	private static final class MutableClock extends Clock {

		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}

		private void advance(Duration duration) {
			instant = instant.plus(duration);
		}
	}
}
