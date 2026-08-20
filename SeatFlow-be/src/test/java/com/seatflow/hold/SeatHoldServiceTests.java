package com.seatflow.hold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.seatflow.event.EventSeatHoldCandidate;
import com.seatflow.event.EventSeatLayoutRow;
import com.seatflow.event.EventSeatMapper;
import com.seatflow.event.EventSeatRecord;
import com.seatflow.event.EventSeatStatus;
import com.seatflow.event.EventStatus;
import com.seatflow.seatupdates.SeatStateChangeType;
import com.seatflow.seatupdates.SeatStateNotifier;

class SeatHoldServiceTests {

	private static final UUID EVENT_ID = UUID.fromString("5d2b80f6-4a0e-4f91-9676-e2c2d8b59e42");
	private static final UUID OTHER_EVENT_ID = UUID.fromString("d7c03613-dcad-4b06-9df3-54c3ea256371");
	private static final UUID EVENT_SEAT_ID = UUID.fromString("0ab96cb6-0b8b-4db4-855e-1bd12f3fc0e5");
	private static final UUID EVENT_SEAT_ID_2 = UUID.fromString("252d8a6e-5dab-4ab2-bcb1-c4baeb017fd2");
	private static final UUID EVENT_SEAT_ID_3 = UUID.fromString("65f02189-cbc6-4097-a8ed-50e6b9464b64");
	private static final UUID SEAT_ID = UUID.fromString("9eaf1782-8239-4c83-a2a0-9b622b468bf0");
	private static final UUID SEAT_ID_2 = UUID.fromString("86b8769e-080e-491f-9290-2eed6ef139e2");
	private static final UUID SEAT_ID_3 = UUID.fromString("12dca0ce-07e1-4bc9-9523-12cfe5ec93a5");
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
		assertThat(response.eventSeatIds()).containsExactly(EVENT_SEAT_ID);
		assertThat(response.userId()).isEqualTo(USER_ID);
		assertThat(response.expiresAt()).isEqualTo(NOW.plus(TTL));
		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isTrue();
		assertThat(store.dataHolds).containsKey(response.holdId());
		assertThat(store.userHolds).containsEntry(USER_ID, response.holdId());
	}

	@Test
	void createsHoldForMultipleSeatsAtomically() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(List.of(
				candidate(EVENT_SEAT_ID, SEAT_ID, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE),
				candidate(EVENT_SEAT_ID_2, SEAT_ID_2, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE)), store, clock);

		SeatHoldResponse response = service.createHold(
				EVENT_ID,
				USER_ID,
				new SeatHoldRequest(null, List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2)));

		assertThat(response.eventSeatId()).isEqualTo(EVENT_SEAT_ID);
		assertThat(response.eventSeatIds()).containsExactly(EVENT_SEAT_ID, EVENT_SEAT_ID_2);
		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isTrue();
		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID_2)).isTrue();
		assertThat(store.dataHolds.get(response.holdId()).eventSeatIds())
				.containsExactly(EVENT_SEAT_ID, EVENT_SEAT_ID_2);
		assertThat(store.dataHolds.get(response.holdId()).seatIds())
				.containsExactly(SEAT_ID, SEAT_ID_2);
	}

	@Test
	void holdCreationBroadcastsHeldSeatsForEvent() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		RecordingSeatStateNotifier notifier = new RecordingSeatStateNotifier();
		SeatHoldService service = service(List.of(
				candidate(EVENT_SEAT_ID, SEAT_ID, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE),
				candidate(EVENT_SEAT_ID_2, SEAT_ID_2, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE)),
				store,
				clock,
				notifier);

		service.createHold(
				EVENT_ID,
				USER_ID,
				new SeatHoldRequest(null, List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2)));

		assertThat(notifier.updates)
				.containsExactly(new SeatStateUpdate(
						SeatStateChangeType.SEATS_HELD,
						EVENT_ID,
						List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2)));
	}

	@Test
	void holdCreationSucceedsWhenBroadcastFails() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		RecordingSeatStateNotifier notifier = new RecordingSeatStateNotifier();
		notifier.failHeld = true;
		SeatHoldService service = service(
				candidate(EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE),
				store,
				clock,
				notifier);

		assertThatCode(() -> service.createHold(EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID)))
				.doesNotThrowAnyException();

		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isTrue();
	}

	@Test
	void rejectsWholeMultiSeatHoldWhenAnyRequestedSeatIsAlreadyHeld() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(List.of(
				candidate(EVENT_SEAT_ID, SEAT_ID, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE),
				candidate(EVENT_SEAT_ID_2, SEAT_ID_2, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE)), store, clock);

		service.createHold(EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID));

		assertThatThrownBy(() -> service.createHold(
				EVENT_ID,
				OTHER_USER_ID,
				new SeatHoldRequest(null, List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2))))
				.isInstanceOf(SeatHoldConflictException.class);

		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isTrue();
		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID_2)).isFalse();
		assertThat(store.dataHolds).hasSize(1);
	}

	@Test
	void rejectsDuplicateSeatIdsBeforeRedisAcquisition() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(List.of(
				candidate(EVENT_SEAT_ID, SEAT_ID, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE),
				candidate(EVENT_SEAT_ID_2, SEAT_ID_2, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE)), store, clock);

		assertThatThrownBy(() -> service.createHold(
				EVENT_ID,
				USER_ID,
				new SeatHoldRequest(null, List.of(EVENT_SEAT_ID, EVENT_SEAT_ID))))
				.isInstanceOf(InvalidSeatHoldRequestException.class);

		assertThat(store.seatHoldCount()).isZero();
		assertThat(store.dataHolds).isEmpty();
	}

	@Test
	void rejectsRequestsOverConfiguredMaximumSeatCount() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(
				List.of(
						candidate(EVENT_SEAT_ID, SEAT_ID, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE),
						candidate(EVENT_SEAT_ID_2, SEAT_ID_2, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE)),
				store,
				clock,
				1);

		assertThatThrownBy(() -> service.createHold(
				EVENT_ID,
				USER_ID,
				new SeatHoldRequest(null, List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2))))
				.isInstanceOf(InvalidSeatHoldRequestException.class);

		assertThat(store.seatHoldCount()).isZero();
		assertThat(store.dataHolds).isEmpty();
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

	@Test
	void onlyOneConcurrentOverlappingMultiSeatRequestWinsCompletely() throws Exception {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(List.of(
				candidate(EVENT_SEAT_ID, SEAT_ID, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE),
				candidate(EVENT_SEAT_ID_2, SEAT_ID_2, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE),
				candidate(EVENT_SEAT_ID_3, SEAT_ID_3, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE)), store, clock);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		AtomicBoolean failed = new AtomicBoolean(false);
		ConcurrentMap<UUID, SeatHoldResponse> successfulUsers = new ConcurrentHashMap<>();

		submitHold(
				executor,
				ready,
				start,
				failed,
				successfulUsers,
				service,
				USER_ID,
				List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2));
		submitHold(
				executor,
				ready,
				start,
				failed,
				successfulUsers,
				service,
				OTHER_USER_ID,
				List.of(EVENT_SEAT_ID_2, EVENT_SEAT_ID_3));

		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		executor.shutdown();
		assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

		assertThat(failed).isFalse();
		assertThat(successfulUsers).hasSize(1);
		SeatHoldResponse winner = successfulUsers.values().iterator().next();
		assertThat(winner.eventSeatIds()).hasSize(2);
		assertThat(store.seatHoldCount()).isEqualTo(2);
		assertThat(store.dataHolds).hasSize(1);
	}

	@Test
	void storageFailureDoesNotLeavePartialMultiSeatHold() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		store.failCreate = true;
		SeatHoldService service = service(List.of(
				candidate(EVENT_SEAT_ID, SEAT_ID, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE),
				candidate(EVENT_SEAT_ID_2, SEAT_ID_2, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE)), store, clock);

		assertThatThrownBy(() -> service.createHold(
				EVENT_ID,
				USER_ID,
				new SeatHoldRequest(null, List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2))))
				.isInstanceOf(SeatHoldStorageException.class);

		assertThat(store.seatHoldCount()).isZero();
		assertThat(store.dataHolds).isEmpty();
		assertThat(store.userHolds).isEmpty();
	}

	@Test
	void ownerCanRetrieveActiveHold() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(List.of(
				candidate(EVENT_SEAT_ID, SEAT_ID, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE),
				candidate(EVENT_SEAT_ID_2, SEAT_ID_2, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE)), store, clock);
		SeatHoldResponse created = service.createHold(
				EVENT_ID,
				USER_ID,
				new SeatHoldRequest(null, List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2)));

		SeatHoldResponse retrieved = service.getHold(created.holdId(), USER_ID);

		assertThat(retrieved.holdId()).isEqualTo(created.holdId());
		assertThat(retrieved.eventId()).isEqualTo(EVENT_ID);
		assertThat(retrieved.eventSeatIds()).containsExactly(EVENT_SEAT_ID, EVENT_SEAT_ID_2);
		assertThat(retrieved.userId()).isEqualTo(USER_ID);
	}

	@Test
	void wrongUserCannotRetrieveHold() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(candidate(EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE), store, clock);
		SeatHoldResponse created = service.createHold(EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID));

		assertThatThrownBy(() -> service.getHold(created.holdId(), OTHER_USER_ID))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void expiredHoldCannotBeRetrieved() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(candidate(EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE), store, clock);
		SeatHoldResponse created = service.createHold(EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID));

		clock.advance(TTL.plusMillis(1));

		assertThatThrownBy(() -> service.getHold(created.holdId(), USER_ID))
				.isInstanceOf(SeatHoldNotFoundException.class);
	}

	@Test
	void ownerCanReleaseMultiSeatHoldAndSeatsBecomeAvailable() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(List.of(
				candidate(EVENT_SEAT_ID, SEAT_ID, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE),
				candidate(EVENT_SEAT_ID_2, SEAT_ID_2, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE)), store, clock);
		SeatHoldResponse created = service.createHold(
				EVENT_ID,
				USER_ID,
				new SeatHoldRequest(null, List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2)));

		service.releaseHold(created.holdId(), USER_ID);

		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isFalse();
		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID_2)).isFalse();
		assertThat(store.dataHolds).doesNotContainKey(created.holdId());
		assertThat(store.userHolds).doesNotContainKey(USER_ID);
		SeatHoldResponse secondHold = service.createHold(EVENT_ID, OTHER_USER_ID, new SeatHoldRequest(EVENT_SEAT_ID));
		assertThat(secondHold.userId()).isEqualTo(OTHER_USER_ID);
	}

	@Test
	void releaseBroadcastsReleasedSeatsForEvent() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		RecordingSeatStateNotifier notifier = new RecordingSeatStateNotifier();
		SeatHoldService service = service(List.of(
				candidate(EVENT_SEAT_ID, SEAT_ID, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE),
				candidate(EVENT_SEAT_ID_2, SEAT_ID_2, EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE)),
				store,
				clock,
				notifier);
		SeatHoldResponse created = service.createHold(
				EVENT_ID,
				USER_ID,
				new SeatHoldRequest(null, List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2)));
		notifier.updates.clear();

		service.releaseHold(created.holdId(), USER_ID);

		assertThat(notifier.updates)
				.containsExactly(new SeatStateUpdate(
						SeatStateChangeType.SEATS_RELEASED,
						EVENT_ID,
						List.of(EVENT_SEAT_ID, EVENT_SEAT_ID_2)));
	}

	@Test
	void releaseSucceedsWhenBroadcastFails() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		RecordingSeatStateNotifier notifier = new RecordingSeatStateNotifier();
		SeatHoldService service = service(
				candidate(EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE),
				store,
				clock,
				notifier);
		SeatHoldResponse created = service.createHold(EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID));
		notifier.failReleased = true;

		assertThatCode(() -> service.releaseHold(created.holdId(), USER_ID))
				.doesNotThrowAnyException();

		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isFalse();
	}

	@Test
	void wrongUserCannotReleaseHold() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(candidate(EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE), store, clock);
		SeatHoldResponse created = service.createHold(EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID));

		assertThatThrownBy(() -> service.releaseHold(created.holdId(), OTHER_USER_ID))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isTrue();
	}

	@Test
	void expiredAndRepeatedReleaseAreIdempotent() {
		MutableClock clock = new MutableClock(NOW);
		FakeSeatHoldStore store = new FakeSeatHoldStore(clock);
		SeatHoldService service = service(candidate(EventStatus.PUBLISHED, EventSeatStatus.AVAILABLE), store, clock);
		SeatHoldResponse created = service.createHold(EVENT_ID, USER_ID, new SeatHoldRequest(EVENT_SEAT_ID));

		clock.advance(TTL.plusMillis(1));

		service.releaseHold(created.holdId(), USER_ID);
		service.releaseHold(created.holdId(), USER_ID);

		assertThat(store.hasSeatHold(EVENT_ID, EVENT_SEAT_ID)).isFalse();
	}

	private static SeatHoldService service(
			EventSeatHoldCandidate candidate,
			FakeSeatHoldStore store,
			Clock clock) {
		return service(candidate == null ? List.of() : List.of(candidate), store, clock);
	}

	private static SeatHoldService service(
			EventSeatHoldCandidate candidate,
			FakeSeatHoldStore store,
			Clock clock,
			SeatStateNotifier seatStateNotifier) {
		return service(
				candidate == null ? List.of() : List.of(candidate),
				store,
				clock,
				8,
				seatStateNotifier);
	}

	private static SeatHoldService service(
			List<EventSeatHoldCandidate> candidates,
			FakeSeatHoldStore store,
			Clock clock) {
		return service(candidates, store, clock, 8);
	}

	private static SeatHoldService service(
			List<EventSeatHoldCandidate> candidates,
			FakeSeatHoldStore store,
			Clock clock,
			SeatStateNotifier seatStateNotifier) {
		return service(candidates, store, clock, 8, seatStateNotifier);
	}

	private static SeatHoldService service(
			List<EventSeatHoldCandidate> candidates,
			FakeSeatHoldStore store,
			Clock clock,
			int maxSeats) {
		return service(candidates, store, clock, maxSeats, new RecordingSeatStateNotifier());
	}

	private static SeatHoldService service(
			List<EventSeatHoldCandidate> candidates,
			FakeSeatHoldStore store,
			Clock clock,
			int maxSeats,
			SeatStateNotifier seatStateNotifier) {
		return new SeatHoldService(
				new StubEventSeatMapper(candidates),
				store,
				new SeatHoldProperties(TTL, maxSeats),
				clock,
				seatStateNotifier);
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
		return candidate(EVENT_SEAT_ID, SEAT_ID, eventStatus, permanentStatus, salesStart, salesEnd);
	}

	private static EventSeatHoldCandidate candidate(
			UUID eventSeatId,
			UUID seatId,
			EventStatus eventStatus,
			EventSeatStatus permanentStatus) {
		return candidate(
				eventSeatId,
				seatId,
				eventStatus,
				permanentStatus,
				NOW.minus(Duration.ofHours(1)),
				NOW.plus(Duration.ofHours(1)));
	}

	private static EventSeatHoldCandidate candidate(
			UUID eventSeatId,
			UUID seatId,
			EventStatus eventStatus,
			EventSeatStatus permanentStatus,
			Instant salesStart,
			Instant salesEnd) {
		return new EventSeatHoldCandidate(
				EVENT_ID,
				eventSeatId,
				seatId,
				eventStatus,
				salesStart,
				salesEnd,
				permanentStatus);
	}

	private static final class StubEventSeatMapper implements EventSeatMapper {

		private final List<EventSeatHoldCandidate> candidates;

		private StubEventSeatMapper(List<EventSeatHoldCandidate> candidates) {
			this.candidates = candidates;
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
		public List<EventSeatRecord> lockByIds(List<UUID> eventSeatIds) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int markSold(UUID id) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<EventSeatLayoutRow> findPublishedLayoutByEventId(UUID eventId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public EventSeatHoldCandidate findHoldCandidate(UUID eventId, UUID eventSeatId) {
			return candidates.stream()
					.filter(candidate -> candidate.eventId().equals(eventId))
					.filter(candidate -> candidate.eventSeatId().equals(eventSeatId))
					.findFirst()
					.orElse(null);
		}

		@Override
		public List<EventSeatHoldCandidate> findHoldCandidates(UUID eventId, List<UUID> eventSeatIds) {
			return candidates.stream()
					.filter(candidate -> candidate.eventId().equals(eventId))
					.filter(candidate -> eventSeatIds.contains(candidate.eventSeatId()))
					.toList();
		}
	}

	private static final class FakeSeatHoldStore implements SeatHoldStore {

		private final Clock clock;
		private final ConcurrentMap<String, SeatEntry> seatHolds = new ConcurrentHashMap<>();
		private final ConcurrentMap<UUID, SeatHoldRecord> dataHolds = new ConcurrentHashMap<>();
		private final ConcurrentMap<UUID, UUID> userHolds = new ConcurrentHashMap<>();
		private boolean failCreate;

		private FakeSeatHoldStore(Clock clock) {
			this.clock = clock;
		}

		@Override
		public boolean createHold(SeatHoldRecord hold, Duration ttl) {
			if (failCreate) {
				throw new IllegalStateException("Lua execution failed");
			}
			Instant now = clock.instant();
			synchronized (seatHolds) {
				for (UUID eventSeatId : hold.eventSeatIds()) {
					SeatEntry existing = seatHolds.get(SeatHoldRedisKeys.seat(hold.eventId(), eventSeatId));
					if (existing != null && existing.expiresAt().isAfter(now)) {
						return false;
					}
				}
				for (UUID eventSeatId : hold.eventSeatIds()) {
					seatHolds.put(
							SeatHoldRedisKeys.seat(hold.eventId(), eventSeatId),
							new SeatEntry(hold.holdId(), now.plus(ttl)));
				}
				dataHolds.put(hold.holdId(), hold);
				userHolds.put(hold.userId(), hold.holdId());
				return true;
			}
		}

		@Override
		public Optional<SeatHoldRecord> findHold(UUID holdId) {
			SeatHoldRecord hold = dataHolds.get(holdId);
			if (hold == null || !hold.expiresAt().isAfter(clock.instant())) {
				return Optional.empty();
			}
			return Optional.of(hold);
		}

		@Override
		public boolean isHoldActive(SeatHoldRecord hold) {
			return hold.eventSeatIds().stream()
					.allMatch(eventSeatId -> {
						SeatEntry entry = seatHolds.get(SeatHoldRedisKeys.seat(hold.eventId(), eventSeatId));
						return entry != null
								&& entry.holdId().equals(hold.holdId())
								&& entry.expiresAt().isAfter(clock.instant());
					});
		}

		@Override
		public void releaseHold(SeatHoldRecord hold) {
			synchronized (seatHolds) {
				for (UUID eventSeatId : hold.eventSeatIds()) {
					String key = SeatHoldRedisKeys.seat(hold.eventId(), eventSeatId);
					SeatEntry entry = seatHolds.get(key);
					if (entry != null && entry.holdId().equals(hold.holdId())) {
						seatHolds.remove(key);
					}
				}
				dataHolds.remove(hold.holdId());
				userHolds.remove(hold.userId(), hold.holdId());
			}
		}

		@Override
		public java.util.Map<UUID, UUID> findActiveSeatHoldOwners(UUID eventId, List<UUID> eventSeatIds) {
			java.util.Map<UUID, UUID> ownersByEventSeatId = new java.util.HashMap<>();
			for (UUID eventSeatId : eventSeatIds) {
				SeatEntry entry = seatHolds.get(SeatHoldRedisKeys.seat(eventId, eventSeatId));
				if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
					continue;
				}
				SeatHoldRecord hold = dataHolds.get(entry.holdId());
				if (hold != null
						&& hold.eventId().equals(eventId)
						&& hold.eventSeatIds().contains(eventSeatId)
						&& hold.expiresAt().isAfter(clock.instant())) {
					ownersByEventSeatId.put(eventSeatId, hold.userId());
				}
			}
			return ownersByEventSeatId;
		}

		private boolean hasSeatHold(UUID eventId, UUID eventSeatId) {
			SeatEntry entry = seatHolds.get(SeatHoldRedisKeys.seat(eventId, eventSeatId));
			return entry != null && entry.expiresAt().isAfter(clock.instant());
		}

		private int seatHoldCount() {
			Instant now = clock.instant();
			return (int) seatHolds.values().stream()
					.filter(entry -> entry.expiresAt().isAfter(now))
					.count();
		}

		private record SeatEntry(UUID holdId, Instant expiresAt) {
		}
	}

	private static final class RecordingSeatStateNotifier implements SeatStateNotifier {

		private final List<SeatStateUpdate> updates = new java.util.ArrayList<>();
		private boolean failHeld;
		private boolean failReleased;
		private boolean failSold;

		@Override
		public void seatsHeld(UUID eventId, List<UUID> eventSeatIds) {
			if (failHeld) {
				throw new IllegalStateException("WebSocket unavailable");
			}
			updates.add(new SeatStateUpdate(SeatStateChangeType.SEATS_HELD, eventId, eventSeatIds));
		}

		@Override
		public void seatsReleased(UUID eventId, List<UUID> eventSeatIds) {
			if (failReleased) {
				throw new IllegalStateException("WebSocket unavailable");
			}
			updates.add(new SeatStateUpdate(SeatStateChangeType.SEATS_RELEASED, eventId, eventSeatIds));
		}

		@Override
		public void seatsSold(UUID eventId, List<UUID> eventSeatIds) {
			if (failSold) {
				throw new IllegalStateException("WebSocket unavailable");
			}
			updates.add(new SeatStateUpdate(SeatStateChangeType.SEATS_SOLD, eventId, eventSeatIds));
		}
	}

	private record SeatStateUpdate(SeatStateChangeType type, UUID eventId, List<UUID> eventSeatIds) {

		private SeatStateUpdate {
			eventSeatIds = List.copyOf(eventSeatIds);
		}
	}

	private static void submitHold(
			java.util.concurrent.ExecutorService executor,
			CountDownLatch ready,
			CountDownLatch start,
			AtomicBoolean failed,
			ConcurrentMap<UUID, SeatHoldResponse> successfulUsers,
			SeatHoldService service,
			UUID userId,
			List<UUID> eventSeatIds) {
		executor.submit(() -> {
			ready.countDown();
			try {
				start.await();
				SeatHoldResponse response = service.createHold(
						EVENT_ID,
						userId,
						new SeatHoldRequest(null, eventSeatIds));
				successfulUsers.put(userId, response);
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
