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

import com.seatflow.seating.SeatMapper;
import com.seatflow.seating.SeatRecord;
import com.seatflow.seating.VenueSectionMapper;
import com.seatflow.seating.VenueSectionRecord;
import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.venue.VenueMapper;
import com.seatflow.venue.VenueRecord;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class EventSeatMapperIntegrationTests extends PostgresTestContainerSupport {

	private static final Instant EVENT_START = Instant.parse("2026-09-01T19:00:00Z");
	private static final Instant SALES_START = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant SALES_END = Instant.parse("2026-09-01T18:00:00Z");

	@Autowired
	private VenueMapper venueMapper;

	@Autowired
	private VenueSectionMapper sectionMapper;

	@Autowired
	private SeatMapper seatMapper;

	@Autowired
	private EventMapper eventMapper;

	@Autowired
	private EventSectionMapper eventSectionMapper;

	@Autowired
	private EventSeatMapper eventSeatMapper;

	@Autowired
	private EventPublishService publishService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void deleteEventSeats() {
		jdbcTemplate.update("DELETE FROM event_seats");
		jdbcTemplate.update("DELETE FROM event_sections");
		jdbcTemplate.update("DELETE FROM events");
		jdbcTemplate.update("DELETE FROM seats");
		jdbcTemplate.update("DELETE FROM venue_sections");
		jdbcTemplate.update("DELETE FROM venues");
	}

	@Test
	void insertForDraftEventGeneratesInventoryWithSectionPrices() {
		VenueRecord venue = insertVenue("Main Hall");
		VenueSectionRecord orchestra = insertSection(venue.id(), "Orchestra", 1);
		VenueSectionRecord balcony = insertSection(venue.id(), "Balcony", 2);
		insertSeat(orchestra.id(), "A", 1, "A1");
		insertSeat(orchestra.id(), "A", 2, "A2");
		insertSeat(balcony.id(), "B", 1, "B1");
		EventRecord event = insertEvent(venue.id(), "Opening Night");
		insertEventSection(event.id(), orchestra.id(), "125000.00", true);
		insertEventSection(event.id(), balcony.id(), "75000.00", false);

		int insertedRows = eventSeatMapper.insertForDraftEvent(event.id());

		List<EventSeatRecord> found = eventSeatMapper.findByEventId(event.id());
		assertThat(insertedRows).isEqualTo(3);
		assertThat(eventSeatMapper.countByEventId(event.id())).isEqualTo(3);
		assertThat(eventSeatMapper.countSourceSeatsForEvent(event.id())).isEqualTo(3);
		assertThat(eventSeatMapper.countMissingPricedSeatsForEvent(event.id())).isZero();
		assertThat(found).hasSize(3);
		assertThat(found.getFirst().price()).isEqualByComparingTo("125000.00");
		assertThat(found.getFirst().permanentStatus()).isEqualTo(EventSeatStatus.AVAILABLE);
		assertThat(found.get(1).price()).isEqualByComparingTo("125000.00");
		assertThat(found.get(1).permanentStatus()).isEqualTo(EventSeatStatus.AVAILABLE);
		assertThat(found.get(2).price()).isEqualByComparingTo("75000.00");
		assertThat(found.get(2).permanentStatus()).isEqualTo(EventSeatStatus.BLOCKED);
		assertThat(found).extracting(EventSeatRecord::version).containsOnly(0);
	}

	@Test
	void missingPricesAreCountedForSeatsInUnpricedSections() {
		VenueRecord venue = insertVenue("Main Hall");
		VenueSectionRecord orchestra = insertSection(venue.id(), "Orchestra", 1);
		VenueSectionRecord balcony = insertSection(venue.id(), "Balcony", 2);
		insertSeat(orchestra.id(), "A", 1, "A1");
		insertSeat(balcony.id(), "B", 1, "B1");
		EventRecord event = insertEvent(venue.id(), "Opening Night");
		insertEventSection(event.id(), orchestra.id(), "125000.00", true);

		assertThat(eventSeatMapper.countSourceSeatsForEvent(event.id())).isEqualTo(2);
		assertThat(eventSeatMapper.countMissingPricedSeatsForEvent(event.id())).isEqualTo(1);
	}

	@Test
	void uniqueConstraintPreventsDuplicateInventoryRows() {
		VenueRecord venue = insertVenue("Main Hall");
		VenueSectionRecord orchestra = insertSection(venue.id(), "Orchestra", 1);
		insertSeat(orchestra.id(), "A", 1, "A1");
		EventRecord event = insertEvent(venue.id(), "Opening Night");
		insertEventSection(event.id(), orchestra.id(), "125000.00", true);

		int firstInsertRows = eventSeatMapper.insertForDraftEvent(event.id());
		int secondInsertRows = eventSeatMapper.insertForDraftEvent(event.id());

		assertThat(firstInsertRows).isEqualTo(1);
		assertThat(secondInsertRows).isZero();
		assertThat(eventSeatMapper.countByEventId(event.id())).isEqualTo(1);
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void publishCreatesInventoryMarksEventPublishedAndIsIdempotent() {
		VenueRecord venue = insertVenue("Main Hall");
		VenueSectionRecord orchestra = insertSection(venue.id(), "Orchestra", 1);
		insertSeat(orchestra.id(), "A", 1, "A1");
		insertSeat(orchestra.id(), "A", 2, "A2");
		EventRecord event = insertEvent(venue.id(), "Opening Night");
		insertEventSection(event.id(), orchestra.id(), "125000.00", true);

		EventPublishResponse firstResponse = publishService.publishEvent(event.id());
		EventPublishResponse secondResponse = publishService.publishEvent(event.id());

		assertThat(firstResponse.status()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(firstResponse.inventoryCount()).isEqualTo(2);
		assertThat(secondResponse.status()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(secondResponse.inventoryCount()).isEqualTo(2);
		assertThat(eventSeatMapper.countByEventId(event.id())).isEqualTo(2);
		assertThat(eventMapper.findById(event.id()).status()).isEqualTo(EventStatus.PUBLISHED);
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void missingPricesRejectPublishAndLeaveEventDraft() {
		VenueRecord venue = insertVenue("Main Hall");
		VenueSectionRecord orchestra = insertSection(venue.id(), "Orchestra", 1);
		insertSeat(orchestra.id(), "A", 1, "A1");
		EventRecord event = insertEvent(venue.id(), "Opening Night");

		assertThatThrownBy(() -> publishService.publishEvent(event.id()))
				.isInstanceOf(MissingEventSectionPricingException.class);
		assertThat(eventSeatMapper.countByEventId(event.id())).isZero();
		assertThat(eventMapper.findById(event.id()).status()).isEqualTo(EventStatus.DRAFT);
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void noSeatsRejectPublishAndLeaveEventDraft() {
		VenueRecord venue = insertVenue("Main Hall");
		VenueSectionRecord orchestra = insertSection(venue.id(), "Orchestra", 1);
		EventRecord event = insertEvent(venue.id(), "Opening Night");
		insertEventSection(event.id(), orchestra.id(), "125000.00", true);

		assertThatThrownBy(() -> publishService.publishEvent(event.id()))
				.isInstanceOf(NoEventSeatsException.class);
		assertThat(eventSeatMapper.countByEventId(event.id())).isZero();
		assertThat(eventMapper.findById(event.id()).status()).isEqualTo(EventStatus.DRAFT);
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void failedPublishRollsBackNewInventoryRows() {
		VenueRecord venue = insertVenue("Main Hall");
		VenueSectionRecord orchestra = insertSection(venue.id(), "Orchestra", 1);
		insertSeat(orchestra.id(), "A", 1, "A1");
		EventRecord event = insertEvent(venue.id(), "Opening Night");
		insertEventSection(event.id(), orchestra.id(), "125000.00", true);
		assertThat(eventSeatMapper.insertForDraftEvent(event.id())).isEqualTo(1);
		insertSeat(orchestra.id(), "A", 2, "A2");

		assertThatThrownBy(() -> publishService.publishEvent(event.id()))
				.isInstanceOf(EventPublicationException.class);
		assertThat(eventSeatMapper.countByEventId(event.id())).isEqualTo(1);
		assertThat(eventMapper.findById(event.id()).status()).isEqualTo(EventStatus.DRAFT);
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

	private SeatRecord insertSeat(UUID sectionId, String rowLabel, int seatNumber, String seatLabel) {
		SeatRecord seat = SeatRecord.forInsert(UUID.randomUUID(), sectionId, rowLabel, seatNumber, seatLabel, false);
		seatMapper.insert(seat);
		return seatMapper.findById(seat.id());
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

	private void insertEventSection(
			UUID eventId,
			UUID sectionId,
			String price,
			boolean salesEnabled) {
		EventSectionRecord section = EventSectionRecord.forInsert(
				UUID.randomUUID(),
				eventId,
				sectionId,
				new BigDecimal(price),
				salesEnabled);
		assertThat(eventSectionMapper.insertForDraftEvent(section)).isEqualTo(1);
	}
}
