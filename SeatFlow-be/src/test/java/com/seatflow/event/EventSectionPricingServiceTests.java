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

class EventSectionPricingServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant EVENT_START = Instant.parse("2026-09-01T19:00:00Z");
	private static final Instant SALES_START = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant SALES_END = Instant.parse("2026-09-01T18:00:00Z");

	@Test
	void replaceDraftEventSectionsStoresPricingConfiguration() {
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		InMemoryEventSectionMapper eventSectionMapper = new InMemoryEventSectionMapper();
		UUID eventId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		UUID sectionId = UUID.randomUUID();
		eventMapper.events.add(event(eventId, venueId, EventStatus.DRAFT));
		eventSectionMapper.allowedVenueSectionIds.add(sectionId);
		EventSectionPricingService pricingService = new EventSectionPricingService(eventMapper, eventSectionMapper);

		EventSectionConfigurationResponse response = pricingService.replaceEventSections(
				eventId,
				new EventSectionReplaceRequest(List.of(item(sectionId, "125000.00", true))));

		assertThat(response.eventId()).isEqualTo(eventId);
		assertThat(response.sections()).hasSize(1);
		assertThat(response.sections().getFirst().venueSectionId()).isEqualTo(sectionId);
		assertThat(response.sections().getFirst().price()).isEqualByComparingTo("125000.00");
		assertThat(response.sections().getFirst().salesEnabled()).isTrue();
	}

	@Test
	void replaceDraftEventSectionsReplacesExistingConfiguration() {
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		InMemoryEventSectionMapper eventSectionMapper = new InMemoryEventSectionMapper();
		UUID eventId = UUID.randomUUID();
		UUID venueId = UUID.randomUUID();
		UUID oldSectionId = UUID.randomUUID();
		UUID newSectionId = UUID.randomUUID();
		eventMapper.events.add(event(eventId, venueId, EventStatus.DRAFT));
		eventSectionMapper.allowedVenueSectionIds.add(newSectionId);
		eventSectionMapper.sections.add(section(eventId, oldSectionId, "50000.00", true));
		EventSectionPricingService pricingService = new EventSectionPricingService(eventMapper, eventSectionMapper);

		EventSectionConfigurationResponse response = pricingService.replaceEventSections(
				eventId,
				new EventSectionReplaceRequest(List.of(item(newSectionId, "75000.00", false))));

		assertThat(response.sections()).hasSize(1);
		assertThat(response.sections().getFirst().venueSectionId()).isEqualTo(newSectionId);
		assertThat(response.sections().getFirst().price()).isEqualByComparingTo("75000.00");
		assertThat(response.sections().getFirst().salesEnabled()).isFalse();
	}

	@Test
	void replaceRejectsInvalidVenueSection() {
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		InMemoryEventSectionMapper eventSectionMapper = new InMemoryEventSectionMapper();
		UUID eventId = UUID.randomUUID();
		eventMapper.events.add(event(eventId, UUID.randomUUID(), EventStatus.DRAFT));
		EventSectionPricingService pricingService = new EventSectionPricingService(eventMapper, eventSectionMapper);

		assertThatThrownBy(() -> pricingService.replaceEventSections(
				eventId,
				new EventSectionReplaceRequest(List.of(item(UUID.randomUUID(), "125000.00", true)))))
				.isInstanceOf(InvalidEventSectionException.class)
				.hasMessage("Invalid event section");
	}

	@Test
	void replaceRejectsNegativePrice() {
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		InMemoryEventSectionMapper eventSectionMapper = new InMemoryEventSectionMapper();
		UUID eventId = UUID.randomUUID();
		UUID sectionId = UUID.randomUUID();
		eventMapper.events.add(event(eventId, UUID.randomUUID(), EventStatus.DRAFT));
		eventSectionMapper.allowedVenueSectionIds.add(sectionId);
		EventSectionPricingService pricingService = new EventSectionPricingService(eventMapper, eventSectionMapper);

		assertThatThrownBy(() -> pricingService.replaceEventSections(
				eventId,
				new EventSectionReplaceRequest(List.of(item(sectionId, "-0.01", true)))))
				.isInstanceOf(InvalidEventSectionPriceException.class)
				.hasMessage("Invalid event section price");
		assertThat(eventSectionMapper.sections).isEmpty();
	}

	@Test
	void replaceRejectsDuplicateSectionInRequest() {
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		InMemoryEventSectionMapper eventSectionMapper = new InMemoryEventSectionMapper();
		UUID eventId = UUID.randomUUID();
		UUID sectionId = UUID.randomUUID();
		eventMapper.events.add(event(eventId, UUID.randomUUID(), EventStatus.DRAFT));
		eventSectionMapper.allowedVenueSectionIds.add(sectionId);
		EventSectionPricingService pricingService = new EventSectionPricingService(eventMapper, eventSectionMapper);

		assertThatThrownBy(() -> pricingService.replaceEventSections(
				eventId,
				new EventSectionReplaceRequest(List.of(
						item(sectionId, "125000.00", true),
						item(sectionId, "150000.00", false)))))
				.isInstanceOf(DuplicateEventSectionException.class)
				.hasMessage("Duplicate event section");
	}

	@Test
	void replaceRejectsPublishedEvent() {
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		InMemoryEventSectionMapper eventSectionMapper = new InMemoryEventSectionMapper();
		UUID eventId = UUID.randomUUID();
		eventMapper.events.add(event(eventId, UUID.randomUUID(), EventStatus.PUBLISHED));
		EventSectionPricingService pricingService = new EventSectionPricingService(eventMapper, eventSectionMapper);

		assertThatThrownBy(() -> pricingService.replaceEventSections(
				eventId,
				new EventSectionReplaceRequest(List.of(item(UUID.randomUUID(), "125000.00", true)))))
				.isInstanceOf(EventStateConflictException.class)
				.hasMessage("Event state conflict");
	}

	@Test
	void getMissingEventSectionsReturnsNotFound() {
		EventSectionPricingService pricingService = new EventSectionPricingService(
				new InMemoryEventMapper(),
				new InMemoryEventSectionMapper());

		assertThatThrownBy(() -> pricingService.getEventSections(UUID.randomUUID()))
				.isInstanceOf(EventNotFoundException.class)
				.hasMessage("Event not found");
	}

	private static EventSectionItemRequest item(UUID venueSectionId, String price, boolean salesEnabled) {
		return new EventSectionItemRequest(venueSectionId, new BigDecimal(price), salesEnabled);
	}

	private static EventRecord event(UUID id, UUID venueId, EventStatus status) {
		return new EventRecord(
				id,
				venueId,
				"Opening Night",
				"Season opener",
				EVENT_START,
				SALES_START,
				SALES_END,
				status,
				NOW,
				NOW);
	}

	private static EventSectionRecord section(
			UUID eventId,
			UUID venueSectionId,
			String price,
			boolean salesEnabled) {
		return new EventSectionRecord(
				UUID.randomUUID(),
				eventId,
				venueSectionId,
				new BigDecimal(price),
				salesEnabled,
				NOW,
				NOW);
	}

	private static final class InMemoryEventMapper implements EventMapper {

		private final List<EventRecord> events = new ArrayList<>();

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

	private static final class InMemoryEventSectionMapper implements EventSectionMapper {

		private final List<UUID> allowedVenueSectionIds = new ArrayList<>();
		private final List<EventSectionRecord> sections = new ArrayList<>();

		@Override
		public int insertForDraftEvent(EventSectionRecord section) {
			if (!allowedVenueSectionIds.contains(section.venueSectionId())) {
				return 0;
			}
			sections.add(withDefaults(section));
			return 1;
		}

		@Override
		public int deleteByEventId(UUID eventId) {
			int before = sections.size();
			sections.removeIf(section -> section.eventId().equals(eventId));
			return before - sections.size();
		}

		@Override
		public List<EventSectionRecord> findByEventId(UUID eventId) {
			return sections.stream()
					.filter(section -> section.eventId().equals(eventId))
					.toList();
		}

		private static EventSectionRecord withDefaults(EventSectionRecord section) {
			return new EventSectionRecord(
					section.id(),
					section.eventId(),
					section.venueSectionId(),
					section.price(),
					section.salesEnabled(),
					section.createdAt() == null ? NOW : section.createdAt(),
					section.updatedAt() == null ? NOW : section.updatedAt());
		}
	}
}
