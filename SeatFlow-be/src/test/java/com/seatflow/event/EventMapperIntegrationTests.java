package com.seatflow.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.venue.VenueMapper;
import com.seatflow.venue.VenueRecord;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class EventMapperIntegrationTests extends PostgresTestContainerSupport {

	private static final Instant EVENT_START = Instant.parse("2026-09-01T19:00:00Z");
	private static final Instant SALES_START = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant SALES_END = Instant.parse("2026-09-01T18:00:00Z");

	@Autowired
	private VenueMapper venueMapper;

	@Autowired
	private EventMapper eventMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void deleteEvents() {
		jdbcTemplate.update("DELETE FROM event_sections");
		jdbcTemplate.update("DELETE FROM events");
		jdbcTemplate.update("DELETE FROM seats");
		jdbcTemplate.update("DELETE FROM venue_sections");
		jdbcTemplate.update("DELETE FROM venues");
	}

	@Test
	void insertsAndFindsEventWithDraftDefault() {
		VenueRecord venue = insertVenue("Main Hall");
		EventRecord event = event(venue.id(), "Opening Night", EVENT_START);

		eventMapper.insert(event);

		EventRecord found = eventMapper.findById(event.id());
		assertThat(found).isNotNull();
		assertThat(found.id()).isEqualTo(event.id());
		assertThat(found.venueId()).isEqualTo(venue.id());
		assertThat(found.name()).isEqualTo("Opening Night");
		assertThat(found.description()).isEqualTo("Season opener");
		assertThat(found.startTime()).isEqualTo(EVENT_START);
		assertThat(found.salesStartTime()).isEqualTo(SALES_START);
		assertThat(found.salesEndTime()).isEqualTo(SALES_END);
		assertThat(found.status()).isEqualTo(EventStatus.DRAFT);
		assertThat(found.createdAt()).isNotNull();
		assertThat(found.updatedAt()).isNotNull();
	}

	@Test
	void updatesDraftEventAndReturnsAffectedRows() {
		VenueRecord firstVenue = insertVenue("First Hall");
		VenueRecord secondVenue = insertVenue("Second Hall");
		EventRecord event = event(firstVenue.id(), "Old Name", EVENT_START);
		eventMapper.insert(event);
		EventRecord update = EventRecord.forUpdate(
				event.id(),
				secondVenue.id(),
				"Updated Name",
				"Updated description",
				EVENT_START.plusSeconds(3600),
				SALES_START,
				SALES_END);

		int updatedRows = eventMapper.update(update);

		EventRecord found = eventMapper.findById(event.id());
		assertThat(updatedRows).isEqualTo(1);
		assertThat(found.venueId()).isEqualTo(secondVenue.id());
		assertThat(found.name()).isEqualTo("Updated Name");
		assertThat(found.description()).isEqualTo("Updated description");
		assertThat(found.startTime()).isEqualTo(EVENT_START.plusSeconds(3600));
		assertThat(found.status()).isEqualTo(EventStatus.DRAFT);
	}

	@Test
	void updateMissingEventReturnsZeroRows() {
		VenueRecord venue = insertVenue("Main Hall");
		EventRecord update = EventRecord.forUpdate(
				UUID.randomUUID(),
				venue.id(),
				"Missing",
				null,
				EVENT_START,
				SALES_START,
				SALES_END);

		assertThat(eventMapper.update(update)).isZero();
	}

	@Test
	void listsEventsWithPaginationAndExplicitOrdering() {
		VenueRecord venue = insertVenue("Main Hall");
		EventRecord later = event(venue.id(), "Later Event", EVENT_START.plusSeconds(7200));
		EventRecord alpha = event(venue.id(), "Alpha Event", EVENT_START);
		EventRecord beta = event(venue.id(), "Beta Event", EVENT_START);
		eventMapper.insert(later);
		eventMapper.insert(beta);
		eventMapper.insert(alpha);

		List<EventRecord> firstPage = eventMapper.findPage(2, 0);
		List<EventRecord> secondPage = eventMapper.findPage(2, 2);

		assertThat(eventMapper.count()).isEqualTo(3);
		assertThat(firstPage).extracting(EventRecord::name).containsExactly("Alpha Event", "Beta Event");
		assertThat(secondPage).extracting(EventRecord::name).containsExactly("Later Event");
	}

	@Test
	void conditionalUpdateRejectsPublishedVenueChange() {
		VenueRecord firstVenue = insertVenue("First Hall");
		VenueRecord secondVenue = insertVenue("Second Hall");
		EventRecord event = event(firstVenue.id(), "Published Event", EVENT_START);
		eventMapper.insert(event);
		jdbcTemplate.update("UPDATE events SET status = 'PUBLISHED' WHERE id = ?", event.id());
		EventRecord update = EventRecord.forUpdate(
				event.id(),
				secondVenue.id(),
				"Published Event Updated",
				"New description",
				EVENT_START.plusSeconds(3600),
				SALES_START,
				SALES_END);

		int updatedRows = eventMapper.update(update);

		EventRecord found = eventMapper.findById(event.id());
		assertThat(updatedRows).isZero();
		assertThat(found.venueId()).isEqualTo(firstVenue.id());
		assertThat(found.name()).isEqualTo("Published Event");
		assertThat(found.status()).isEqualTo(EventStatus.PUBLISHED);
	}

	@Test
	void conditionalUpdateAllowsPublishedEventWhenVenueDoesNotChange() {
		VenueRecord venue = insertVenue("Main Hall");
		EventRecord event = event(venue.id(), "Published Event", EVENT_START);
		eventMapper.insert(event);
		jdbcTemplate.update("UPDATE events SET status = 'PUBLISHED' WHERE id = ?", event.id());
		EventRecord update = EventRecord.forUpdate(
				event.id(),
				venue.id(),
				"Published Event Updated",
				"New description",
				EVENT_START.plusSeconds(3600),
				SALES_START,
				SALES_END);

		int updatedRows = eventMapper.update(update);

		EventRecord found = eventMapper.findById(event.id());
		assertThat(updatedRows).isEqualTo(1);
		assertThat(found.name()).isEqualTo("Published Event Updated");
		assertThat(found.venueId()).isEqualTo(venue.id());
		assertThat(found.status()).isEqualTo(EventStatus.PUBLISHED);
	}

	private VenueRecord insertVenue(String name) {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(),
				name,
				"1 Event Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh");
		venueMapper.insert(venue);
		return venueMapper.findById(venue.id());
	}

	private static EventRecord event(UUID venueId, String name, Instant startTime) {
		return EventRecord.forInsert(
				UUID.randomUUID(),
				venueId,
				name,
				"Season opener",
				startTime,
				SALES_START,
				SALES_END);
	}
}
