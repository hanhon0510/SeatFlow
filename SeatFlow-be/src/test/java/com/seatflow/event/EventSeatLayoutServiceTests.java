package com.seatflow.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EventSeatLayoutServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

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
		EventSeatLayoutService service = new EventSeatLayoutService(mapper);

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
		assertThat(response.sections().getFirst().rows().getFirst().seats().getFirst().accessible()).isTrue();
		assertThat(response.sections().getFirst().rows().getFirst().seats().get(1).permanentStatus())
				.isEqualTo(EventSeatStatus.SOLD);
		assertThat(response.sections().getFirst().rows().get(1).seats().getFirst().permanentStatus())
				.isEqualTo(EventSeatStatus.BLOCKED);
		assertThat(response.sections().get(1).name()).isEqualTo("Balcony");
	}

	@Test
	void emptyPublishedLayoutIsUnavailablePublicly() {
		EventSeatLayoutService service = new EventSeatLayoutService(new InMemoryEventSeatMapper(List.of()));

		assertThatThrownBy(() -> service.getSeatLayout(UUID.randomUUID()))
				.isInstanceOf(EventNotFoundException.class)
				.hasMessage("Event not found");
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
}
