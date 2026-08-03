package com.seatflow.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.seating.VenueSectionMapper;
import com.seatflow.seating.VenueSectionRecord;
import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.venue.VenueMapper;
import com.seatflow.venue.VenueRecord;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class EventSectionMapperIntegrationTests extends PostgresTestContainerSupport {

	private static final Instant EVENT_START = Instant.parse("2026-09-01T19:00:00Z");
	private static final Instant SALES_START = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant SALES_END = Instant.parse("2026-09-01T18:00:00Z");

	@Autowired
	private VenueMapper venueMapper;

	@Autowired
	private VenueSectionMapper sectionMapper;

	@Autowired
	private EventMapper eventMapper;

	@Autowired
	private EventSectionMapper eventSectionMapper;

	@Autowired
	private EventSectionPricingService pricingService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void deleteEventSections() {
		jdbcTemplate.update("DELETE FROM event_seats");
		jdbcTemplate.update("DELETE FROM event_sections");
		jdbcTemplate.update("DELETE FROM events");
		jdbcTemplate.update("DELETE FROM seats");
		jdbcTemplate.update("DELETE FROM venue_sections");
		jdbcTemplate.update("DELETE FROM venues");
	}

	@Test
	void insertsConfigurationForDraftEvent() {
		VenueRecord venue = insertVenue("Main Hall");
		VenueSectionRecord section = insertSection(venue.id(), "Orchestra", 1);
		EventRecord event = insertEvent(venue.id(), "Opening Night");
		EventSectionRecord configuration = eventSection(event.id(), section.id(), "125000.00", true);

		int insertedRows = eventSectionMapper.insertForDraftEvent(configuration);

		List<EventSectionRecord> found = eventSectionMapper.findByEventId(event.id());
		assertThat(insertedRows).isEqualTo(1);
		assertThat(found).hasSize(1);
		assertThat(found.getFirst().eventId()).isEqualTo(event.id());
		assertThat(found.getFirst().venueSectionId()).isEqualTo(section.id());
		assertThat(found.getFirst().price()).isEqualByComparingTo("125000.00");
		assertThat(found.getFirst().salesEnabled()).isTrue();
		assertThat(found.getFirst().createdAt()).isNotNull();
		assertThat(found.getFirst().updatedAt()).isNotNull();
	}

	@Test
	void replacesConfigurationWithStableSectionOrdering() {
		VenueRecord venue = insertVenue("Main Hall");
		VenueSectionRecord balcony = insertSection(venue.id(), "Balcony", 2);
		VenueSectionRecord orchestra = insertSection(venue.id(), "Orchestra", 1);
		EventRecord event = insertEvent(venue.id(), "Opening Night");
		eventSectionMapper.insertForDraftEvent(eventSection(event.id(), balcony.id(), "90000.00", true));

		eventSectionMapper.deleteByEventId(event.id());
		eventSectionMapper.insertForDraftEvent(eventSection(event.id(), balcony.id(), "75000.00", false));
		eventSectionMapper.insertForDraftEvent(eventSection(event.id(), orchestra.id(), "125000.00", true));

		List<EventSectionRecord> found = eventSectionMapper.findByEventId(event.id());
		assertThat(found).extracting(EventSectionRecord::venueSectionId)
				.containsExactly(orchestra.id(), balcony.id());
		assertThat(found.getFirst().price()).isEqualByComparingTo("125000.00");
		assertThat(found.get(1).salesEnabled()).isFalse();
	}

	@Test
	void invalidVenueSectionReturnsZeroRows() {
		VenueRecord eventVenue = insertVenue("Event Hall");
		VenueRecord otherVenue = insertVenue("Other Hall");
		VenueSectionRecord otherVenueSection = insertSection(otherVenue.id(), "Balcony", 1);
		EventRecord event = insertEvent(eventVenue.id(), "Opening Night");

		int insertedRows = eventSectionMapper.insertForDraftEvent(
				eventSection(event.id(), otherVenueSection.id(), "125000.00", true));

		assertThat(insertedRows).isZero();
		assertThat(eventSectionMapper.findByEventId(event.id())).isEmpty();
	}

	@Test
	void negativePriceIsRejectedByDatabase() {
		VenueRecord venue = insertVenue("Main Hall");
		VenueSectionRecord section = insertSection(venue.id(), "Orchestra", 1);
		EventRecord event = insertEvent(venue.id(), "Opening Night");

		assertThatThrownBy(() -> eventSectionMapper.insertForDraftEvent(
				eventSection(event.id(), section.id(), "-0.01", true)))
				.isInstanceOf(RuntimeException.class);
		assertThat(eventSectionMapper.findByEventId(event.id())).isEmpty();
	}

	@Test
	void publishedEventPricingInsertReturnsZeroRows() {
		VenueRecord venue = insertVenue("Main Hall");
		VenueSectionRecord section = insertSection(venue.id(), "Orchestra", 1);
		EventRecord event = insertEvent(venue.id(), "Opening Night");
		jdbcTemplate.update("UPDATE events SET status = 'PUBLISHED' WHERE id = ?", event.id());

		int insertedRows = eventSectionMapper.insertForDraftEvent(
				eventSection(event.id(), section.id(), "125000.00", true));

		assertThat(insertedRows).isZero();
		assertThat(eventSectionMapper.findByEventId(event.id())).isEmpty();
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void failedReplacementRollsBackExistingConfiguration() {
		VenueRecord eventVenue = insertVenue("Event Hall");
		VenueRecord otherVenue = insertVenue("Other Hall");
		VenueSectionRecord oldSection = insertSection(eventVenue.id(), "Old Section", 1);
		VenueSectionRecord newSection = insertSection(eventVenue.id(), "New Section", 2);
		VenueSectionRecord invalidSection = insertSection(otherVenue.id(), "Invalid Section", 1);
		EventRecord event = insertEvent(eventVenue.id(), "Opening Night");
		eventSectionMapper.insertForDraftEvent(eventSection(event.id(), oldSection.id(), "50000.00", true));

		assertThatThrownBy(() -> pricingService.replaceEventSections(
				event.id(),
				new EventSectionReplaceRequest(List.of(
						new EventSectionItemRequest(newSection.id(), new BigDecimal("75000.00"), true),
						new EventSectionItemRequest(invalidSection.id(), new BigDecimal("90000.00"), false)))))
				.isInstanceOf(InvalidEventSectionException.class);
		assertThat(eventSectionMapper.findByEventId(event.id()))
				.extracting(EventSectionRecord::venueSectionId)
				.containsExactly(oldSection.id());
	}

	private VenueRecord insertVenue(String name) {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(),
				"%s %s".formatted(name, UUID.randomUUID()),
				"1 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh");
		venueMapper.insert(venue);
		return venueMapper.findById(venue.id());
	}

	private VenueSectionRecord insertSection(UUID venueId, String name, int displayOrder) {
		VenueSectionRecord section = VenueSectionRecord.forInsert(UUID.randomUUID(), venueId, name, displayOrder);
		sectionMapper.insert(section);
		return sectionMapper.findById(section.id());
	}

	private EventRecord insertEvent(UUID venueId, String name) {
		EventRecord event = EventRecord.forInsert(
				UUID.randomUUID(),
				venueId,
				name,
				"Season opener",
				EVENT_START,
				SALES_START,
				SALES_END);
		eventMapper.insert(event);
		return eventMapper.findById(event.id());
	}

	private static EventSectionRecord eventSection(
			UUID eventId,
			UUID sectionId,
			String price,
			boolean salesEnabled) {
		return EventSectionRecord.forInsert(
				UUID.randomUUID(),
				eventId,
				sectionId,
				new BigDecimal(price),
				salesEnabled);
	}
}
