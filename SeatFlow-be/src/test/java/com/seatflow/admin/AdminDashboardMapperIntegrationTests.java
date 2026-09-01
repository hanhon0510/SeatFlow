package com.seatflow.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.event.EventStatus;
import com.seatflow.support.PostgresTestContainerSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AdminDashboardMapperIntegrationTests extends PostgresTestContainerSupport {

	private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

	@Autowired
	private AdminDashboardMapper adminDashboardMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void deleteCatalogData() {
		jdbcTemplate.update("DELETE FROM event_seats");
		jdbcTemplate.update("DELETE FROM event_sections");
		jdbcTemplate.update("DELETE FROM events");
		jdbcTemplate.update("DELETE FROM seats");
		jdbcTemplate.update("DELETE FROM venue_sections");
		jdbcTemplate.update("DELETE FROM venues");
	}

	@Test
	void venueSummaryCountsVenuesSectionsAndSeats() {
		UUID activeVenue = insertVenue("Main Hall", "ACTIVE");
		insertVenue("Old Hall", "ARCHIVED");
		UUID section = insertSection(activeVenue, "Orchestra", 1);
		insertSeat(section, "A", 1);
		insertSeat(section, "A", 2);

		AdminDashboardVenues venues = adminDashboardMapper.findVenueSummary();

		assertThat(venues.total()).isEqualTo(2);
		assertThat(venues.active()).isEqualTo(1);
		assertThat(venues.archived()).isEqualTo(1);
		assertThat(venues.sections()).isEqualTo(1);
		assertThat(venues.seats()).isEqualTo(2);
	}

	@Test
	void venueSummaryCountsAVenueWithoutSeatsAsZeroSeats() {
		insertVenue("Empty Hall", "ACTIVE");

		AdminDashboardVenues venues = adminDashboardMapper.findVenueSummary();

		assertThat(venues.total()).isEqualTo(1);
		assertThat(venues.sections()).isZero();
		assertThat(venues.seats()).isZero();
	}

	@Test
	void eventSummaryCountsStatusesSalesWindowAndStartingSoon() {
		UUID venue = insertVenue("Main Hall", "ACTIVE");
		// On sale and starting in three days.
		insertEvent(venue, "On sale", EventStatus.PUBLISHED, NOW.plus(Duration.ofDays(3)), NOW.minusSeconds(60), NOW.plus(Duration.ofDays(2)));
		// Published but sales have not opened, and it starts well beyond the week.
		insertEvent(venue, "Later", EventStatus.PUBLISHED, NOW.plus(Duration.ofDays(40)), NOW.plus(Duration.ofDays(20)), NOW.plus(Duration.ofDays(39)));
		insertEvent(venue, "Draft", EventStatus.DRAFT, NOW.plus(Duration.ofDays(5)), NOW, NOW.plus(Duration.ofDays(4)));
		insertEvent(venue, "Cancelled", EventStatus.CANCELLED, NOW.plus(Duration.ofDays(6)), NOW, NOW.plus(Duration.ofDays(5)));

		AdminDashboardEvents events = adminDashboardMapper.findEventSummary(NOW, NOW.plus(Duration.ofDays(7)));

		assertThat(events.total()).isEqualTo(4);
		assertThat(events.draft()).isEqualTo(1);
		assertThat(events.published()).isEqualTo(2);
		assertThat(events.cancelled()).isEqualTo(1);
		assertThat(events.completed()).isZero();
		assertThat(events.onSaleNow()).isEqualTo(1);
		assertThat(events.startingSoon()).isEqualTo(1);
	}

	@Test
	void upcomingEventsAreOrderedByStartTimeAndCountSoldSeats() {
		UUID venue = insertVenue("Main Hall", "ACTIVE");
		UUID section = insertSection(venue, "Orchestra", 1);
		UUID seatOne = insertSeat(section, "A", 1);
		UUID seatTwo = insertSeat(section, "A", 2);
		UUID later = insertEvent(venue, "Later", EventStatus.PUBLISHED, NOW.plus(Duration.ofDays(10)), NOW, NOW.plus(Duration.ofDays(9)));
		UUID sooner = insertEvent(venue, "Sooner", EventStatus.PUBLISHED, NOW.plus(Duration.ofDays(2)), NOW, NOW.plus(Duration.ofDays(1)));
		insertEvent(venue, "Already started", EventStatus.PUBLISHED, NOW.minus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(9)), NOW.minus(Duration.ofDays(2)));
		insertEventSeat(sooner, seatOne, "SOLD");
		insertEventSeat(sooner, seatTwo, "AVAILABLE");

		List<AdminDashboardUpcomingEvent> upcoming = adminDashboardMapper.findUpcomingEvents(NOW, 5);

		assertThat(upcoming).extracting(AdminDashboardUpcomingEvent::name).containsExactly("Sooner", "Later");
		assertThat(upcoming.getFirst().eventId()).isEqualTo(sooner);
		assertThat(upcoming.getFirst().venueName()).isEqualTo("Main Hall");
		assertThat(upcoming.getFirst().seatsTotal()).isEqualTo(2);
		assertThat(upcoming.getFirst().seatsSold()).isEqualTo(1);
		assertThat(upcoming.get(1).eventId()).isEqualTo(later);
		assertThat(upcoming.get(1).seatsTotal()).isZero();
	}

	@Test
	void upcomingEventsHonourTheLimit() {
		UUID venue = insertVenue("Main Hall", "ACTIVE");
		for (int index = 1; index <= 4; index++) {
			insertEvent(venue, "Event " + index, EventStatus.PUBLISHED, NOW.plus(Duration.ofDays(index)), NOW, NOW.plus(Duration.ofDays(index)));
		}

		assertThat(adminDashboardMapper.findUpcomingEvents(NOW, 2)).hasSize(2);
	}

	@Test
	void salesQueriesReturnEmptyTotalsWhenNothingHasBeenSold() {
		assertThat(adminDashboardMapper.countOrdersByStatus("PAID")).isZero();
		assertThat(adminDashboardMapper.countTicketsByStatus("ACTIVE")).isZero();
		assertThat(adminDashboardMapper.findRevenueByCurrency()).isEmpty();
	}

	private UUID insertVenue(String name, String status) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO venues (id, name, address, city, country, timezone, status)
				VALUES (?, ?, '1 Event Street', 'Ho Chi Minh City', 'Vietnam', 'Asia/Ho_Chi_Minh', ?)
				""", id, name, status);
		return id;
	}

	private UUID insertSection(UUID venueId, String name, int displayOrder) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO venue_sections (id, venue_id, name, display_order) VALUES (?, ?, ?, ?)",
				id, venueId, name, displayOrder);
		return id;
	}

	private UUID insertSeat(UUID sectionId, String rowLabel, int seatNumber) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO seats (id, section_id, row_label, seat_number, seat_label, accessible)
				VALUES (?, ?, ?, ?, ?, TRUE)
				""", id, sectionId, rowLabel, seatNumber, rowLabel + seatNumber);
		return id;
	}

	private UUID insertEvent(
			UUID venueId,
			String name,
			EventStatus status,
			Instant startTime,
			Instant salesStartTime,
			Instant salesEndTime) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO events (id, venue_id, name, start_time, sales_start_time, sales_end_time, status)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""",
				id,
				venueId,
				name,
				java.sql.Timestamp.from(startTime),
				java.sql.Timestamp.from(salesStartTime),
				java.sql.Timestamp.from(salesEndTime),
				status.name());
		return id;
	}

	private void insertEventSeat(UUID eventId, UUID seatId, String permanentStatus) {
		jdbcTemplate.update("""
				INSERT INTO event_seats (id, event_id, seat_id, price, permanent_status)
				VALUES (?, ?, ?, 100.00, ?)
				""", UUID.randomUUID(), eventId, seatId, permanentStatus);
	}
}
