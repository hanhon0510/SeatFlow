package com.seatflow.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EventPublishServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant EVENT_START = Instant.parse("2026-09-01T19:00:00Z");
	private static final Instant SALES_START = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant SALES_END = Instant.parse("2026-09-01T18:00:00Z");

	@Test
	void publishDraftEventCreatesInventoryAndMarksPublished() {
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		InMemoryEventSeatMapper eventSeatMapper = new InMemoryEventSeatMapper();
		UUID eventId = UUID.randomUUID();
		eventMapper.events.add(event(eventId, EventStatus.DRAFT));
		eventSeatMapper.sourceSeatCount = 3;
		eventSeatMapper.insertedRows = 3;
		eventSeatMapper.inventoryCount = 3;
		EventPublishService publishService = new EventPublishService(eventMapper, eventSeatMapper);

		EventPublishResponse response = publishService.publishEvent(eventId);

		assertThat(response.eventId()).isEqualTo(eventId);
		assertThat(response.status()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(response.inventoryCount()).isEqualTo(3);
		assertThat(eventMapper.findById(eventId).status()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(eventSeatMapper.insertCalls).isEqualTo(1);
	}

	@Test
	void publishRejectsMissingSectionPricing() {
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		InMemoryEventSeatMapper eventSeatMapper = new InMemoryEventSeatMapper();
		UUID eventId = UUID.randomUUID();
		eventMapper.events.add(event(eventId, EventStatus.DRAFT));
		eventSeatMapper.sourceSeatCount = 2;
		eventSeatMapper.missingPricedSeatCount = 1;
		EventPublishService publishService = new EventPublishService(eventMapper, eventSeatMapper);

		assertThatThrownBy(() -> publishService.publishEvent(eventId))
				.isInstanceOf(MissingEventSectionPricingException.class)
				.hasMessage("Event section pricing is incomplete");
		assertThat(eventMapper.findById(eventId).status()).isEqualTo(EventStatus.DRAFT);
		assertThat(eventSeatMapper.insertCalls).isZero();
	}

	@Test
	void publishRejectsEventWithoutSeats() {
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		InMemoryEventSeatMapper eventSeatMapper = new InMemoryEventSeatMapper();
		UUID eventId = UUID.randomUUID();
		eventMapper.events.add(event(eventId, EventStatus.DRAFT));
		EventPublishService publishService = new EventPublishService(eventMapper, eventSeatMapper);

		assertThatThrownBy(() -> publishService.publishEvent(eventId))
				.isInstanceOf(NoEventSeatsException.class)
				.hasMessage("Event has no seats");
		assertThat(eventSeatMapper.insertCalls).isZero();
	}

	@Test
	void publishAlreadyPublishedEventIsIdempotent() {
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		InMemoryEventSeatMapper eventSeatMapper = new InMemoryEventSeatMapper();
		UUID eventId = UUID.randomUUID();
		eventMapper.events.add(event(eventId, EventStatus.PUBLISHED));
		eventSeatMapper.inventoryCount = 2;
		EventPublishService publishService = new EventPublishService(eventMapper, eventSeatMapper);

		EventPublishResponse response = publishService.publishEvent(eventId);

		assertThat(response.status()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(response.inventoryCount()).isEqualTo(2);
		assertThat(eventSeatMapper.insertCalls).isZero();
		assertThat(eventMapper.publishCalls).isZero();
	}

	@Test
	void publishDetectsInventoryRowCountMismatch() {
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		InMemoryEventSeatMapper eventSeatMapper = new InMemoryEventSeatMapper();
		UUID eventId = UUID.randomUUID();
		eventMapper.events.add(event(eventId, EventStatus.DRAFT));
		eventSeatMapper.sourceSeatCount = 2;
		eventSeatMapper.insertedRows = 1;
		EventPublishService publishService = new EventPublishService(eventMapper, eventSeatMapper);

		assertThatThrownBy(() -> publishService.publishEvent(eventId))
				.isInstanceOf(EventPublicationException.class)
				.hasMessage("Event publication failed");
		assertThat(eventMapper.findById(eventId).status()).isEqualTo(EventStatus.DRAFT);
	}

	@Test
	void publishRejectsCancelledEvent() {
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		UUID eventId = UUID.randomUUID();
		eventMapper.events.add(event(eventId, EventStatus.CANCELLED));
		EventPublishService publishService = new EventPublishService(eventMapper, new InMemoryEventSeatMapper());

		assertThatThrownBy(() -> publishService.publishEvent(eventId))
				.isInstanceOf(EventStateConflictException.class)
				.hasMessage("Event state conflict");
	}

	private static EventRecord event(UUID id, EventStatus status) {
		return new EventRecord(
				id,
				UUID.randomUUID(),
				"Opening Night",
				"Season opener",
				EVENT_START,
				SALES_START,
				SALES_END,
				status,
				NOW,
				NOW);
	}

	private static final class InMemoryEventMapper implements EventMapper {

		private final List<EventRecord> events = new ArrayList<>();
		private int publishCalls;

		@Override
		public void insert(EventRecord event) {
			events.add(event);
		}

		@Override
		public int update(EventRecord event) {
			return 0;
		}

		@Override
		public int publishDraft(UUID id) {
			publishCalls++;
			for (int index = 0; index < events.size(); index++) {
				EventRecord existing = events.get(index);
				if (existing.id().equals(id) && existing.status() == EventStatus.DRAFT) {
					events.set(index, new EventRecord(
							existing.id(),
							existing.venueId(),
							existing.name(),
							existing.description(),
							existing.startTime(),
							existing.salesStartTime(),
							existing.salesEndTime(),
							EventStatus.PUBLISHED,
							existing.createdAt(),
							NOW.plusSeconds(1)));
					return 1;
				}
			}
			return 0;
		}

		@Override
		public EventRecord findById(UUID id) {
			return events.stream()
					.filter(event -> event.id().equals(id))
					.findFirst()
					.orElse(null);
		}

		@Override
		public EventRecord findByIdForUpdate(UUID id) {
			return findById(id);
		}

		@Override
		public List<EventRecord> findPage(int limit, long offset) {
			return events.stream()
					.sorted(Comparator.comparing(EventRecord::startTime)
							.thenComparing(EventRecord::name)
							.thenComparing(EventRecord::id))
					.skip(offset)
					.limit(limit)
					.toList();
		}

		@Override
		public long count() {
			return events.size();
		}

		@Override
		public List<PublicEventCatalogRecord> findPublishedCatalogPage(PublicEventCatalogQuery query) {
			return List.of();
		}

		@Override
		public long countPublishedCatalog(PublicEventCatalogQuery query) {
			return 0;
		}

		@Override
		public PublicEventCatalogRecord findPublishedCatalogById(UUID id) {
			return null;
		}
	}

	private static final class InMemoryEventSeatMapper implements EventSeatMapper {

		private long inventoryCount;
		private long sourceSeatCount;
		private long missingPricedSeatCount;
		private int insertedRows;
		private int insertCalls;

		@Override
		public int insertForDraftEvent(UUID eventId) {
			insertCalls++;
			return insertedRows;
		}

		@Override
		public long countByEventId(UUID eventId) {
			return inventoryCount;
		}

		@Override
		public long countSourceSeatsForEvent(UUID eventId) {
			return sourceSeatCount;
		}

		@Override
		public long countMissingPricedSeatsForEvent(UUID eventId) {
			return missingPricedSeatCount;
		}

		@Override
		public List<EventSeatRecord> findByEventId(UUID eventId) {
			return List.of(new EventSeatRecord(
					UUID.randomUUID(),
					eventId,
					UUID.randomUUID(),
					new BigDecimal("125000.00"),
					EventSeatStatus.AVAILABLE,
					0,
					NOW,
					NOW));
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
		public List<EventSeatLayoutRow> findPublishedLayoutByEventId(UUID eventId, UUID sectionId) {
			return List.of();
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
