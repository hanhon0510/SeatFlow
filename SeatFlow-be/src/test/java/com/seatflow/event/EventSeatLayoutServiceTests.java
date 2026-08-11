package com.seatflow.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.seatflow.hold.SeatHoldRecord;
import com.seatflow.hold.SeatHoldStore;

class EventSeatLayoutServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
	private static final UUID CURRENT_USER_ID = UUID.fromString("c144397b-1b17-4a45-a1ef-b30ef84d5a79");
	private static final UUID OTHER_USER_ID = UUID.fromString("d3329056-39f7-457e-a27c-95b9d52dfdf6");

	@Test
	void groupsRowsIntoSectionsAndSeatRows() {
		UUID eventId = UUID.randomUUID();
		UUID orchestraId = UUID.randomUUID();
		UUID balconyId = UUID.randomUUID();
		UUID firstSeatId = UUID.randomUUID();
		InMemoryEventSeatMapper mapper = new InMemoryEventSeatMapper(List.of(
				row(orchestraId, "Orchestra", 1, "A", firstSeatId, "A1", 1, "125000.00",
						EventSeatStatus.AVAILABLE, true),
				row(orchestraId, "Orchestra", 1, "A", UUID.randomUUID(), "A2", 2, "125000.00",
						EventSeatStatus.SOLD, false),
				row(orchestraId, "Orchestra", 1, "B", UUID.randomUUID(), "B1", 1, "125000.00",
						EventSeatStatus.BLOCKED, false),
				row(balconyId, "Balcony", 2, "C", UUID.randomUUID(), "C1", 1, "75000.00",
						EventSeatStatus.AVAILABLE, false)));
		EventSeatLayoutService service = new EventSeatLayoutService(mapper, new InMemorySeatHoldStore());

		EventSeatLayoutResponse response = service.getSeatLayout(eventId);

		assertThat(mapper.requestedEventId).isEqualTo(eventId);
		assertThat(response.eventId()).isEqualTo(eventId);
		assertThat(response.sections()).hasSize(2);
		assertThat(response.sections().getFirst().id()).isEqualTo(orchestraId);
		assertThat(response.sections().getFirst().rows()).extracting(EventSeatLayoutRowResponse::rowLabel)
				.containsExactly("A", "B");
		assertThat(response.sections().getFirst().rows().getFirst().seats())
				.extracting(EventSeatLayoutSeatResponse::eventSeatId)
				.containsExactly(firstSeatId, mapper.rows.get(1).eventSeatId());
		assertThat(response.sections().getFirst().rows().getFirst().seats().getFirst().price())
				.isEqualByComparingTo("125000.00");
		assertThat(response.sections().getFirst().rows().getFirst().seats().getFirst().permanentStatus())
				.isEqualTo(EventSeatStatus.AVAILABLE);
		assertThat(response.sections().getFirst().rows().getFirst().seats().getFirst().status())
				.isEqualTo(EventSeatLayoutStatus.AVAILABLE);
		assertThat(response.sections().getFirst().rows().getFirst().seats().getFirst().accessible()).isTrue();
		assertThat(response.sections().getFirst().rows().getFirst().seats().get(1).permanentStatus())
				.isEqualTo(EventSeatStatus.SOLD);
		assertThat(response.sections().getFirst().rows().getFirst().seats().get(1).status())
				.isEqualTo(EventSeatLayoutStatus.SOLD);
		assertThat(response.sections().getFirst().rows().get(1).seats().getFirst().permanentStatus())
				.isEqualTo(EventSeatStatus.BLOCKED);
		assertThat(response.sections().getFirst().rows().get(1).seats().getFirst().status())
				.isEqualTo(EventSeatLayoutStatus.BLOCKED);
		assertThat(response.sections().get(1).name()).isEqualTo("Balcony");
	}

	@Test
	void emptyPublishedLayoutIsUnavailablePublicly() {
		EventSeatLayoutService service = new EventSeatLayoutService(
				new InMemoryEventSeatMapper(List.of()),
				new InMemorySeatHoldStore());

		assertThatThrownBy(() -> service.getSeatLayout(UUID.randomUUID()))
				.isInstanceOf(EventNotFoundException.class)
				.hasMessage("Event not found");
	}

	@Test
	void appliesTemporaryHoldStatusWithoutExposingOwnerDetails() {
		UUID eventId = UUID.randomUUID();
		UUID sectionId = UUID.randomUUID();
		UUID currentUserSeatId = UUID.randomUUID();
		UUID otherUserSeatId = UUID.randomUUID();
		UUID freeSeatId = UUID.randomUUID();
		UUID soldSeatId = UUID.randomUUID();
		InMemorySeatHoldStore holdStore = new InMemorySeatHoldStore();
		holdStore.hold(currentUserSeatId, CURRENT_USER_ID);
		holdStore.hold(otherUserSeatId, OTHER_USER_ID);
		holdStore.hold(soldSeatId, CURRENT_USER_ID);
		EventSeatLayoutService service = new EventSeatLayoutService(new InMemoryEventSeatMapper(List.of(
				row(sectionId, "Orchestra", 1, "A", currentUserSeatId, "A1", 1, "125000.00",
						EventSeatStatus.AVAILABLE, false),
				row(sectionId, "Orchestra", 1, "A", otherUserSeatId, "A2", 2, "125000.00",
						EventSeatStatus.AVAILABLE, false),
				row(sectionId, "Orchestra", 1, "A", freeSeatId, "A3", 3, "125000.00",
						EventSeatStatus.AVAILABLE, false),
				row(sectionId, "Orchestra", 1, "A", soldSeatId, "A4", 4, "125000.00",
						EventSeatStatus.SOLD, false))), holdStore);

		List<EventSeatLayoutSeatResponse> seats = service.getSeatLayout(eventId, CURRENT_USER_ID)
				.sections().getFirst()
				.rows().getFirst()
				.seats();

		assertThat(seats).extracting(EventSeatLayoutSeatResponse::status)
				.containsExactly(
						EventSeatLayoutStatus.HELD_BY_YOU,
						EventSeatLayoutStatus.HELD,
						EventSeatLayoutStatus.AVAILABLE,
						EventSeatLayoutStatus.SOLD);
	}

	@Test
	void redisUnavailableFallsBackToPermanentStatuses() {
		UUID eventId = UUID.randomUUID();
		UUID sectionId = UUID.randomUUID();
		InMemorySeatHoldStore holdStore = new InMemorySeatHoldStore();
		holdStore.unavailable = true;
		EventSeatLayoutService service = new EventSeatLayoutService(new InMemoryEventSeatMapper(List.of(
				row(sectionId, "Orchestra", 1, "A", UUID.randomUUID(), "A1", 1, "125000.00",
						EventSeatStatus.AVAILABLE, false),
				row(sectionId, "Orchestra", 1, "A", UUID.randomUUID(), "A2", 2, "125000.00",
						EventSeatStatus.SOLD, false),
				row(sectionId, "Orchestra", 1, "A", UUID.randomUUID(), "A3", 3, "125000.00",
						EventSeatStatus.BLOCKED, false))), holdStore);

		List<EventSeatLayoutSeatResponse> seats = service.getSeatLayout(eventId, CURRENT_USER_ID)
				.sections().getFirst()
				.rows().getFirst()
				.seats();

		assertThat(seats).extracting(EventSeatLayoutSeatResponse::status)
				.containsExactly(
						EventSeatLayoutStatus.AVAILABLE,
						EventSeatLayoutStatus.SOLD,
						EventSeatLayoutStatus.BLOCKED);
	}

	private static EventSeatLayoutRow row(
			UUID sectionId,
			String sectionName,
			int displayOrder,
			String rowLabel,
			UUID eventSeatId,
			String seatLabel,
			int seatNumber,
			String price,
			EventSeatStatus status,
			boolean accessible) {
		return new EventSeatLayoutRow(
				sectionId,
				sectionName,
				displayOrder,
				rowLabel,
				eventSeatId,
				seatLabel,
				seatNumber,
				new BigDecimal(price),
				status,
				accessible);
	}

	private static final class InMemoryEventSeatMapper implements EventSeatMapper {

		private final List<EventSeatLayoutRow> rows;
		private UUID requestedEventId;

		private InMemoryEventSeatMapper(List<EventSeatLayoutRow> rows) {
			this.rows = rows;
		}

		@Override
		public int insertForDraftEvent(UUID eventId) {
			return 0;
		}

		@Override
		public long countByEventId(UUID eventId) {
			return 0;
		}

		@Override
		public long countSourceSeatsForEvent(UUID eventId) {
			return 0;
		}

		@Override
		public long countMissingPricedSeatsForEvent(UUID eventId) {
			return 0;
		}

		@Override
		public List<EventSeatRecord> findByEventId(UUID eventId) {
			return List.of();
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
			requestedEventId = eventId;
			return rows;
		}

		@Override
		public EventSeatHoldCandidate findHoldCandidate(UUID eventId, UUID eventSeatId) {
			return null;
		}

		@Override
		public List<EventSeatHoldCandidate> findHoldCandidates(UUID eventId, List<UUID> eventSeatIds) {
			return List.of();
		}
	}

	private static final class InMemorySeatHoldStore implements SeatHoldStore {

		private final Map<UUID, UUID> ownersByEventSeatId = new HashMap<>();
		private boolean unavailable;

		private void hold(UUID eventSeatId, UUID userId) {
			ownersByEventSeatId.put(eventSeatId, userId);
		}

		@Override
		public boolean createHold(SeatHoldRecord hold, Duration ttl) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<SeatHoldRecord> findHold(UUID holdId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isHoldActive(SeatHoldRecord hold) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void releaseHold(SeatHoldRecord hold) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<UUID, UUID> findActiveSeatHoldOwners(UUID eventId, List<UUID> eventSeatIds) {
			if (unavailable) {
				throw new IllegalStateException("Redis unavailable");
			}
			Map<UUID, UUID> owners = new HashMap<>();
			eventSeatIds.forEach(eventSeatId -> {
				UUID ownerId = ownersByEventSeatId.get(eventSeatId);
				if (ownerId != null) {
					owners.put(eventSeatId, ownerId);
				}
			});
			return owners;
		}
	}
}
