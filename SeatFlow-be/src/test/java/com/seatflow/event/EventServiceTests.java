package com.seatflow.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.seatflow.venue.ArchivedVenueCannotHostEventsException;
import com.seatflow.venue.VenueMapper;
import com.seatflow.venue.VenueRecord;
import com.seatflow.venue.VenueService;
import com.seatflow.venue.VenueStatus;

class EventServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant EVENT_START = Instant.parse("2026-09-01T19:00:00Z");
	private static final Instant SALES_START = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant SALES_END = Instant.parse("2026-09-01T18:00:00Z");

	@Test
	void createCreatesDraftEventForActiveVenue() {
		InMemoryVenueMapper venueMapper = new InMemoryVenueMapper();
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		VenueRecord venue = venue("Active Hall", VenueStatus.ACTIVE);
		venueMapper.insert(venue);
		EventService eventService = eventService(eventMapper, venueMapper);

		EventResponse response = eventService.createEvent(new EventCreateRequest(
				venue.id(),
				"  Opening Night  ",
				"  Season opener  ",
				EVENT_START,
				SALES_START,
				EVENT_START));

		assertThat(response.id()).isNotNull();
		assertThat(response.venueId()).isEqualTo(venue.id());
		assertThat(response.name()).isEqualTo("Opening Night");
		assertThat(response.description()).isEqualTo("Season opener");
		assertThat(response.status()).isEqualTo(EventStatus.DRAFT);
		assertThat(eventMapper.events).hasSize(1);
	}

	@Test
	void createRejectsSalesStartAtEventStart() {
		EventService eventService = eventService(new InMemoryEventMapper(), new InMemoryVenueMapper());

		assertThatThrownBy(() -> eventService.createEvent(createRequest(UUID.randomUUID(), EVENT_START, EVENT_START)))
				.isInstanceOf(InvalidEventTimingException.class)
				.hasMessage("Sales start must precede event start");
	}

	@Test
	void createRejectsSalesEndAfterEventStart() {
		EventService eventService = eventService(new InMemoryEventMapper(), new InMemoryVenueMapper());

		assertThatThrownBy(() -> eventService.createEvent(new EventCreateRequest(
				UUID.randomUUID(),
				"Opening Night",
				null,
				EVENT_START,
				SALES_START,
				EVENT_START.plusSeconds(1))))
				.isInstanceOf(InvalidEventTimingException.class)
				.hasMessage("Sales end cannot follow event start");
	}

	@Test
	void createRejectsArchivedVenue() {
		InMemoryVenueMapper venueMapper = new InMemoryVenueMapper();
		VenueRecord venue = venue("Archived Hall", VenueStatus.ARCHIVED);
		venueMapper.insert(venue);
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		EventService eventService = eventService(eventMapper, venueMapper);

		assertThatThrownBy(() -> eventService.createEvent(createRequest(venue.id(), SALES_START, SALES_END)))
				.isInstanceOf(ArchivedVenueCannotHostEventsException.class)
				.hasMessage("Archived venue cannot host new events");
		assertThat(eventMapper.events).isEmpty();
	}

	@Test
	void updateRejectsPublishedVenueChangeAsStateConflict() {
		InMemoryVenueMapper venueMapper = new InMemoryVenueMapper();
		VenueRecord firstVenue = venue("First Hall", VenueStatus.ACTIVE);
		VenueRecord secondVenue = venue("Second Hall", VenueStatus.ACTIVE);
		venueMapper.insert(firstVenue);
		venueMapper.insert(secondVenue);
		InMemoryEventMapper eventMapper = new InMemoryEventMapper();
		EventRecord event = existingEvent(firstVenue.id(), EventStatus.PUBLISHED);
		eventMapper.events.add(event);
		EventService eventService = eventService(eventMapper, venueMapper);

		assertThatThrownBy(() -> eventService.updateEvent(
				event.id(),
				updateRequest(secondVenue.id(), "Updated Published Event")))
				.isInstanceOf(EventStateConflictException.class)
				.hasMessage("Event state conflict");
		assertThat(eventMapper.findById(event.id()).venueId()).isEqualTo(firstVenue.id());
	}

	@Test
	void updateMissingEventReturnsNotFoundAfterConditionalUpdateMiss() {
		InMemoryVenueMapper venueMapper = new InMemoryVenueMapper();
		VenueRecord venue = venue("Active Hall", VenueStatus.ACTIVE);
		venueMapper.insert(venue);
		EventService eventService = eventService(new InMemoryEventMapper(), venueMapper);

		assertThatThrownBy(() -> eventService.updateEvent(UUID.randomUUID(), updateRequest(venue.id(), "Missing")))
				.isInstanceOf(EventNotFoundException.class)
				.hasMessage("Event not found");
	}

	@Test
	void listRejectsPageSizeAboveMaximum() {
		EventService eventService = eventService(new InMemoryEventMapper(), new InMemoryVenueMapper());

		assertThatThrownBy(() -> eventService.listEvents(0, EventService.MAX_PAGE_SIZE + 1))
				.isInstanceOf(InvalidEventPaginationException.class)
				.hasMessage("Invalid event pagination");
	}

	private static EventService eventService(InMemoryEventMapper eventMapper, InMemoryVenueMapper venueMapper) {
		return new EventService(eventMapper, new VenueService(venueMapper));
	}

	private static EventCreateRequest createRequest(UUID venueId, Instant salesStartTime, Instant salesEndTime) {
		return new EventCreateRequest(
				venueId,
				"Opening Night",
				"Season opener",
				EVENT_START,
				salesStartTime,
				salesEndTime);
	}

	private static EventUpdateRequest updateRequest(UUID venueId, String name) {
		return new EventUpdateRequest(
				venueId,
				name,
				"Updated description",
				EVENT_START.plusSeconds(3600),
				SALES_START,
				SALES_END);
	}

	private static VenueRecord venue(String name, VenueStatus status) {
		return new VenueRecord(
				UUID.randomUUID(),
				name,
				"1 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh",
				status,
				NOW,
				NOW);
	}

	private static EventRecord existingEvent(UUID venueId, EventStatus status) {
		return new EventRecord(
				UUID.randomUUID(),
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

	private static final class InMemoryEventMapper implements EventMapper {

		private final List<EventRecord> events = new ArrayList<>();

		@Override
		public void insert(EventRecord event) {
			events.add(withDefaults(event));
		}

		@Override
		public int update(EventRecord event) {
			for (int index = 0; index < events.size(); index++) {
				EventRecord existing = events.get(index);
				if (existing.id().equals(event.id())) {
					if (existing.status() == EventStatus.PUBLISHED && !existing.venueId().equals(event.venueId())) {
						return 0;
					}
					events.set(index, new EventRecord(
							existing.id(),
							event.venueId(),
							event.name(),
							event.description(),
							event.startTime(),
							event.salesStartTime(),
							event.salesEndTime(),
							existing.status(),
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

		private static EventRecord withDefaults(EventRecord event) {
			return new EventRecord(
					event.id(),
					event.venueId(),
					event.name(),
					event.description(),
					event.startTime(),
					event.salesStartTime(),
					event.salesEndTime(),
					event.status() == null ? EventStatus.DRAFT : event.status(),
					event.createdAt() == null ? NOW : event.createdAt(),
					event.updatedAt() == null ? NOW : event.updatedAt());
		}
	}

	private static final class InMemoryVenueMapper implements VenueMapper {

		private final List<VenueRecord> venues = new ArrayList<>();

		@Override
		public void insert(VenueRecord venue) {
			venues.add(venue);
		}

		@Override
		public int update(VenueRecord venue) {
			return 0;
		}

		@Override
		public VenueRecord findById(UUID id) {
			return venues.stream()
					.filter(venue -> venue.id().equals(id))
					.findFirst()
					.orElse(null);
		}

		@Override
		public List<VenueRecord> findPage(int limit, long offset) {
			return List.of();
		}

		@Override
		public long count() {
			return venues.size();
		}

		@Override
		public int archive(UUID id) {
			return 0;
		}
	}
}
