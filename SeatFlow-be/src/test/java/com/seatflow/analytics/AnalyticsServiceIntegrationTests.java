package com.seatflow.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.support.PostgresTestContainerSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AnalyticsServiceIntegrationTests extends PostgresTestContainerSupport {

	private static final String BUYER_EMAIL = "analytics-buyer@example.com";
	private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
	private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

	@Autowired
	private AnalyticsMapper analyticsMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private AnalyticsService service;
	private UUID venue;
	private UUID buyer;

	@BeforeEach
	void resetData() {
		jdbcTemplate.update("DELETE FROM waitlist_entries");
		jdbcTemplate.update("DELETE FROM refunds");
		jdbcTemplate.update("DELETE FROM tickets");
		jdbcTemplate.update("DELETE FROM payments");
		jdbcTemplate.update("DELETE FROM orders");
		jdbcTemplate.update("DELETE FROM reservation_items");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM event_seats");
		jdbcTemplate.update("DELETE FROM event_sections");
		jdbcTemplate.update("DELETE FROM events");
		jdbcTemplate.update("DELETE FROM seats");
		jdbcTemplate.update("DELETE FROM venue_sections");
		jdbcTemplate.update("DELETE FROM venues");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'analytics-%@example.com'");

		service = new AnalyticsService(analyticsMapper, Clock.fixed(NOW, ZoneOffset.UTC));
		venue = insertVenue();
		buyer = insertUser();
	}

	@Test
	void countsPaidAndRefundedOrdersButNotUnpaidOnes() {
		UUID event = insertEvent("Opening Night");
		sell(event, "PAID", "200.00", "USD", 2, TODAY);
		sell(event, "REFUNDED", "100.00", "USD", 1, TODAY);
		sell(event, "PENDING", "500.00", "USD", 1, TODAY);
		sell(event, "FAILED", "500.00", "USD", 1, TODAY);
		sell(event, "CANCELLED", "500.00", "USD", 1, TODAY);

		AnalyticsReport report = service.getReport(TODAY, TODAY, null);

		assertThat(report.totals()).singleElement().satisfies(total -> {
			assertThat(total.currency()).isEqualTo("USD");
			assertThat(total.orders()).isEqualTo(2);
			assertThat(total.seats()).isEqualTo(3);
			assertThat(total.gross()).isEqualByComparingTo("300.00");
			assertThat(total.refunded()).isEqualByComparingTo("100.00");
			assertThat(total.net()).isEqualByComparingTo("200.00");
			assertThat(total.refundedOrders()).isEqualTo(1);
		});
	}

	@Test
	void keepsCurrenciesApartAndAddsEventsTogetherPerDay() {
		UUID first = insertEvent("First");
		UUID second = insertEvent("Second");
		sell(first, "PAID", "100.00", "USD", 1, TODAY);
		sell(second, "PAID", "50.00", "USD", 1, TODAY);
		sell(second, "PAID", "70.00", "EUR", 2, TODAY.minusDays(1));

		AnalyticsReport report = service.getReport(TODAY.minusDays(1), TODAY, null);

		assertThat(report.totals()).extracting(AnalyticsCurrencyTotal::currency).containsExactly("EUR", "USD");
		assertThat(report.daily()).extracting(AnalyticsDailyPoint::date, AnalyticsDailyPoint::currency,
				AnalyticsDailyPoint::orders)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(TODAY.minusDays(1), "EUR", 1L),
						org.assertj.core.groups.Tuple.tuple(TODAY, "USD", 2L));
		assertThat(report.daily().get(1).gross()).isEqualByComparingTo("150.00");
	}

	@Test
	void includesBothEndDaysAndNothingOutsideThem() {
		UUID event = insertEvent("Opening Night");
		sell(event, "PAID", "10.00", "USD", 1, TODAY.minusDays(3));
		sell(event, "PAID", "20.00", "USD", 1, TODAY.minusDays(2));
		sell(event, "PAID", "40.00", "USD", 1, TODAY.minusDays(1));
		jdbcTemplate.update("UPDATE orders SET created_at = ?, updated_at = ? WHERE total_amount = 40.00",
				Timestamp.from(Instant.parse("2026-09-09T23:59:59Z")),
				Timestamp.from(Instant.parse("2026-09-09T23:59:59Z")));

		AnalyticsReport report = service.getReport(TODAY.minusDays(2), TODAY.minusDays(1), null);

		assertThat(report.totals()).singleElement()
				.satisfies(total -> assertThat(total.gross()).isEqualByComparingTo("60.00"));
	}

	@Test
	void narrowsEverythingToOneEvent() {
		UUID first = insertEvent("First");
		UUID second = insertEvent("Second");
		sell(first, "PAID", "100.00", "USD", 1, TODAY);
		sell(second, "PAID", "50.00", "USD", 1, TODAY);
		insertReservation(first, "EXPIRED", TODAY);
		insertReservation(second, "EXPIRED", TODAY);
		insertWaitlist(first, "WAITING", false, TODAY);
		insertWaitlist(second, "WAITING", false, TODAY);

		AnalyticsReport report = service.getReport(TODAY, TODAY, first);

		assertThat(report.eventId()).isEqualTo(first);
		assertThat(report.totals()).singleElement()
				.satisfies(total -> assertThat(total.gross()).isEqualByComparingTo("100.00"));
		assertThat(report.topEvents()).extracting(AnalyticsEventTotal::eventName).containsExactly("First");
		// The sale's own confirmed checkout plus the expired one.
		assertThat(report.checkout().started()).isEqualTo(2);
		assertThat(report.waitlist().joined()).isEqualTo(1);
	}

	@Test
	void ranksEventsByNetRevenueAfterRefunds() {
		UUID big = insertEvent("Big but refunded");
		UUID small = insertEvent("Small");
		sell(big, "PAID", "100.00", "USD", 1, TODAY);
		sell(big, "REFUNDED", "400.00", "USD", 1, TODAY);
		sell(small, "PAID", "150.00", "USD", 1, TODAY);

		AnalyticsReport report = service.getReport(TODAY, TODAY, null);

		assertThat(report.topEvents()).extracting(AnalyticsEventTotal::eventName)
				.containsExactly("Small", "Big but refunded");
	}

	@Test
	void showsWhatBecameOfCheckoutsAndWaitlistEntries() {
		UUID event = insertEvent("Opening Night");
		insertReservation(event, "CONFIRMED", TODAY);
		insertReservation(event, "CONFIRMED", TODAY);
		insertReservation(event, "EXPIRED", TODAY);
		insertReservation(event, "PAYMENT_FAILED", TODAY);
		insertReservation(event, "PENDING_PAYMENT", TODAY);
		insertReservation(event, "CANCELLED", TODAY);
		insertReservation(event, "CONFIRMED", TODAY.minusDays(40));
		insertWaitlist(event, "WAITING", false, TODAY);
		insertWaitlist(event, "OFFERED", true, TODAY);
		insertWaitlist(event, "FULFILLED", true, TODAY);
		insertWaitlist(event, "EXPIRED", true, TODAY);
		insertWaitlist(event, "CANCELLED", false, TODAY);

		AnalyticsReport report = service.getReport(TODAY, TODAY, null);

		assertThat(report.checkout()).isEqualTo(new AnalyticsCheckout(6, 2, 1, 1, 1, 1));
		assertThat(report.waitlist()).isEqualTo(new AnalyticsWaitlist(5, 3, 1, 1, 1));
	}

	@Test
	void defaultsToTheLastThirtyDays() {
		AnalyticsReport report = service.getReport(null, null, null);

		assertThat(report.to()).isEqualTo(TODAY);
		assertThat(report.from()).isEqualTo(TODAY.minusDays(29));
		assertThat(report.totals()).isEmpty();
		assertThat(report.daily()).isEmpty();
		assertThat(report.checkout()).isEqualTo(new AnalyticsCheckout(0, 0, 0, 0, 0, 0));
	}

	@Test
	void refusesABackwardsOrTooLongRange() {
		assertThatThrownBy(() -> service.getReport(TODAY, TODAY.minusDays(1), null))
				.isInstanceOf(InvalidAnalyticsRangeException.class);
		assertThatThrownBy(() -> service.getReport(TODAY.minusDays(366), TODAY, null))
				.isInstanceOf(InvalidAnalyticsRangeException.class);
		assertThat(service.getReport(TODAY.minusDays(365), TODAY, null)).isNotNull();
	}

	@Test
	void exportsOneLinePerDayEventAndCurrency() {
		UUID event = insertEvent("Opening Night");
		sell(event, "PAID", "120.50", "USD", 2, TODAY);
		sell(event, "REFUNDED", "20.00", "USD", 1, TODAY);

		String csv = service.exportCsv(TODAY, TODAY, null);

		assertThat(csv.lines().toList()).containsExactly(
				"date,event_id,event,currency,orders,seats,gross,refunded,net",
				TODAY + "," + event + ",Opening Night,USD,2,3,140.50,20.00,120.50");
		assertThat(service.exportFileName(TODAY, TODAY))
				.isEqualTo("seatflow-sales-2026-09-10-to-2026-09-10.csv");
	}

	@Test
	void exportKeepsAnEventNameFromBeingRunAsAFormula() {
		UUID event = insertEvent("=HYPERLINK(\"http://evil\"),x");
		sell(event, "PAID", "10.00", "USD", 1, TODAY);

		List<String> lines = service.exportCsv(TODAY, TODAY, null).lines().toList();

		assertThat(lines.get(1)).contains(",\"'=HYPERLINK(\"\"http://evil\"\"),x\",USD,");
	}

	private void sell(UUID event, String status, String amount, String currency, int seats, LocalDate day) {
		UUID reservation = insertReservation(event, "CONFIRMED", day);
		for (int index = 0; index < seats; index++) {
			insertReservationItem(reservation, event, index, day);
		}
		Instant at = day.atTime(10, 0).toInstant(ZoneOffset.UTC);
		jdbcTemplate.update("""
				INSERT INTO orders (
					id, reservation_id, user_id, status, total_amount, currency, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""",
				UUID.randomUUID(), reservation, buyer, status, new BigDecimal(amount), currency,
				Timestamp.from(at), Timestamp.from(at));
	}

	private UUID insertVenue() {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO venues (id, name, address, city, country, timezone, status)
				VALUES (?, 'Main Hall', '1 Event Street', 'Ho Chi Minh City', 'Vietnam', 'Asia/Ho_Chi_Minh', 'ACTIVE')
				""", id);
		return id;
	}

	private UUID insertUser() {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO users (id, email, password_hash, role, status)
				VALUES (?, ?, 'password-hash', 'USER', 'ACTIVE')
				""", id, BUYER_EMAIL);
		return id;
	}

	private UUID insertEvent(String name) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO events (id, venue_id, name, start_time, sales_start_time, sales_end_time, status)
				VALUES (?, ?, ?, ?, ?, ?, 'PUBLISHED')
				""",
				id, venue, name,
				Timestamp.from(NOW.plusSeconds(86400L * 20)),
				Timestamp.from(NOW.minusSeconds(86400L * 60)),
				Timestamp.from(NOW.plusSeconds(86400L * 19)));
		return id;
	}

	private UUID insertReservation(UUID event, String status, LocalDate day) {
		UUID id = UUID.randomUUID();
		Instant at = day.atTime(9, 0).toInstant(ZoneOffset.UTC);
		jdbcTemplate.update("""
				INSERT INTO reservations (
					id, user_id, event_id, hold_id, status, expires_at, total_amount, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, 100.00, ?, ?)
				""",
				id, buyer, event, UUID.randomUUID(), status,
				Timestamp.from(at.plusSeconds(600)), Timestamp.from(at), Timestamp.from(at));
		return id;
	}

	private void insertReservationItem(UUID reservation, UUID event, int index, LocalDate day) {
		UUID section = UUID.randomUUID();
		UUID seat = UUID.randomUUID();
		UUID eventSeat = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO venue_sections (id, venue_id, name, display_order) VALUES (?, ?, ?, 1)",
				section, venue, "Section " + section);
		jdbcTemplate.update("""
				INSERT INTO seats (id, section_id, row_label, seat_number, seat_label, accessible)
				VALUES (?, ?, 'A', ?, ?, FALSE)
				""", seat, section, index + 1, "A" + (index + 1));
		jdbcTemplate.update("""
				INSERT INTO event_seats (id, event_id, seat_id, price, permanent_status)
				VALUES (?, ?, ?, 10.00, 'SOLD')
				""", eventSeat, event, seat);
		jdbcTemplate.update("""
				INSERT INTO reservation_items (id, reservation_id, event_seat_id, price, created_at)
				VALUES (?, ?, ?, 10.00, ?)
				""", UUID.randomUUID(), reservation, eventSeat,
				Timestamp.from(day.atTime(9, 0).toInstant(ZoneOffset.UTC)));
	}

	private void insertWaitlist(UUID event, String status, boolean offered, LocalDate day) {
		UUID user = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO users (id, email, password_hash, role, status)
				VALUES (?, ?, 'password-hash', 'USER', 'ACTIVE')
				""", user, "analytics-waiter-" + user + "@example.com");
		Instant at = day.atTime(8, 0).toInstant(ZoneOffset.UTC);
		boolean open = "OFFERED".equals(status);
		jdbcTemplate.update("""
				INSERT INTO waitlist_entries (
					id, event_id, user_id, seat_count, status, created_at, offered_at, offer_expires_at, updated_at)
				VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?)
				""",
				UUID.randomUUID(), event, user, status, Timestamp.from(at),
				offered ? Timestamp.from(at.plusSeconds(60)) : null,
				offered || open ? Timestamp.from(at.plusSeconds(660)) : null,
				Timestamp.from(at));
	}
}
