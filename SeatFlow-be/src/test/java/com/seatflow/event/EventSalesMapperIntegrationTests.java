package com.seatflow.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.order.OrderStatus;
import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.ticket.TicketStatus;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class EventSalesMapperIntegrationTests extends PostgresTestContainerSupport {

	private static final String BUYER_EMAIL = "buyer@example.com";
	private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
	private static final Instant EVENT_START = NOW.plus(Duration.ofDays(10));
	private static final Instant SALES_START = NOW.minus(Duration.ofDays(5));
	private static final Instant SALES_END = NOW.plus(Duration.ofDays(9));

	@Autowired
	private EventSalesMapper eventSalesMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void deleteSalesData() {
		jdbcTemplate.update("DELETE FROM tickets");
		jdbcTemplate.update("DELETE FROM orders");
		jdbcTemplate.update("DELETE FROM reservation_items");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM event_seats");
		jdbcTemplate.update("DELETE FROM event_sections");
		jdbcTemplate.update("DELETE FROM events");
		jdbcTemplate.update("DELETE FROM seats");
		jdbcTemplate.update("DELETE FROM venue_sections");
		jdbcTemplate.update("DELETE FROM venues");
		// Only the buyer this class creates: the shared container also holds the seeded local admin.
		jdbcTemplate.update("DELETE FROM users WHERE email = ?", BUYER_EMAIL);
	}

	@Test
	void eventHeaderCarriesTheVenueAndTheDerivedSalesStatus() {
		UUID venue = insertVenue("Main Hall");
		UUID section = insertSection(venue, "Orchestra", 1);
		UUID event = insertEvent(venue, "Opening Night", EventStatus.PUBLISHED);
		insertEventSeat(event, insertSeat(section, "A", 1), EventSeatStatus.AVAILABLE, "100.00");

		EventSalesEventResponse header = eventSalesMapper.findEvent(event, NOW);

		assertThat(header).isNotNull();
		assertThat(header.name()).isEqualTo("Opening Night");
		assertThat(header.venueName()).isEqualTo("Main Hall");
		assertThat(header.venueTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
		assertThat(header.status()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(header.salesStatus()).isEqualTo(EventSalesStatus.ON_SALE);
	}

	@Test
	void aPublishedEventWithoutAvailableSeatsReadsAsSoldOut() {
		UUID venue = insertVenue("Main Hall");
		UUID section = insertSection(venue, "Orchestra", 1);
		UUID event = insertEvent(venue, "Opening Night", EventStatus.PUBLISHED);
		insertEventSeat(event, insertSeat(section, "A", 1), EventSeatStatus.SOLD, "100.00");

		assertThat(eventSalesMapper.findEvent(event, NOW).salesStatus()).isEqualTo(EventSalesStatus.SOLD_OUT);
	}

	@Test
	void aDraftEventHasNoSalesStatusAtAll() {
		UUID venue = insertVenue("Main Hall");
		UUID event = insertEvent(venue, "Not Published Yet", EventStatus.DRAFT);

		EventSalesEventResponse header = eventSalesMapper.findEvent(event, NOW);

		assertThat(header.status()).isEqualTo(EventStatus.DRAFT);
		assertThat(header.salesStatus()).isNull();
	}

	@Test
	void anUnknownEventHasNoHeader() {
		assertThat(eventSalesMapper.findEvent(UUID.randomUUID(), NOW)).isNull();
	}

	@Test
	void inventorySplitsSeatsByStatusAndCountsSeatsStillInCheckout() {
		UUID venue = insertVenue("Main Hall");
		UUID section = insertSection(venue, "Orchestra", 1);
		UUID event = insertEvent(venue, "Opening Night", EventStatus.PUBLISHED);
		UUID sold = insertEventSeat(event, insertSeat(section, "A", 1), EventSeatStatus.SOLD, "100.00");
		insertEventSeat(event, insertSeat(section, "A", 2), EventSeatStatus.AVAILABLE, "100.00");
		insertEventSeat(event, insertSeat(section, "A", 3), EventSeatStatus.BLOCKED, "100.00");
		UUID pendingSeat = insertEventSeat(event, insertSeat(section, "A", 4), EventSeatStatus.AVAILABLE, "100.00");

		UUID buyer = insertUser(BUYER_EMAIL);
		UUID liveReservation = insertReservation(buyer, event, "PENDING_PAYMENT", NOW.plus(Duration.ofMinutes(5)));
		insertReservationItem(liveReservation, pendingSeat, "100.00");
		// An expired reservation no longer holds anything, so its seat must not be counted.
		UUID staleReservation = insertReservation(buyer, event, "PENDING_PAYMENT", NOW.minus(Duration.ofMinutes(5)));
		insertReservationItem(staleReservation, sold, "100.00");

		EventSalesInventoryResponse inventory = eventSalesMapper.findInventory(event, NOW);

		assertThat(inventory.seatsTotal()).isEqualTo(4);
		assertThat(inventory.seatsSold()).isEqualTo(1);
		assertThat(inventory.seatsAvailable()).isEqualTo(2);
		assertThat(inventory.seatsBlocked()).isEqualTo(1);
		assertThat(inventory.seatsInCheckout()).isEqualTo(1);
		assertThat(inventory.inventoryValue()).isEqualByComparingTo("400.00");
		assertThat(inventory.soldValue()).isEqualByComparingTo("100.00");
	}

	@Test
	void inventoryOfAnEventWithoutSeatsIsAllZeroes() {
		UUID venue = insertVenue("Main Hall");
		UUID event = insertEvent(venue, "Not Published Yet", EventStatus.DRAFT);

		EventSalesInventoryResponse inventory = eventSalesMapper.findInventory(event, NOW);

		assertThat(inventory.seatsTotal()).isZero();
		assertThat(inventory.seatsInCheckout()).isZero();
		assertThat(inventory.inventoryValue()).isEqualByComparingTo("0");
		assertThat(inventory.soldValue()).isEqualByComparingTo("0");
	}

	@Test
	void revenueAndOrderCountsCoverOnlyThisEvent() {
		UUID venue = insertVenue("Main Hall");
		UUID section = insertSection(venue, "Orchestra", 1);
		UUID event = insertEvent(venue, "Opening Night", EventStatus.PUBLISHED);
		UUID otherEvent = insertEvent(venue, "Other Night", EventStatus.PUBLISHED);
		UUID seat = insertEventSeat(event, insertSeat(section, "A", 1), EventSeatStatus.SOLD, "100.00");
		UUID buyer = insertUser(BUYER_EMAIL);

		UUID paidReservation = insertReservation(buyer, event, "CONFIRMED", NOW.plus(Duration.ofMinutes(5)));
		insertReservationItem(paidReservation, seat, "100.00");
		insertOrder(paidReservation, buyer, OrderStatus.PAID, "100.00", "VND", NOW.minus(Duration.ofDays(1)));

		UUID pendingReservation = insertReservation(buyer, event, "PENDING_PAYMENT", NOW.plus(Duration.ofMinutes(5)));
		insertOrder(pendingReservation, buyer, OrderStatus.PENDING, "40.00", "VND", NOW);

		UUID otherReservation = insertReservation(buyer, otherEvent, "CONFIRMED", NOW.plus(Duration.ofMinutes(5)));
		insertOrder(otherReservation, buyer, OrderStatus.PAID, "999.00", "VND", NOW);

		List<EventSalesRevenueResponse> revenue = eventSalesMapper.findRevenueByCurrency(event);

		assertThat(revenue).hasSize(1);
		assertThat(revenue.getFirst().currency()).isEqualTo("VND");
		assertThat(revenue.getFirst().paidAmount()).isEqualByComparingTo("100.00");
		assertThat(revenue.getFirst().paidOrders()).isEqualTo(1);
		assertThat(revenue.getFirst().pendingAmount()).isEqualByComparingTo("40.00");
		assertThat(revenue.getFirst().pendingOrders()).isEqualTo(1);

		EventSalesOrderCounts counts = eventSalesMapper.findOrderCounts(event);
		assertThat(counts.total()).isEqualTo(2);
		assertThat(counts.paid()).isEqualTo(1);
		assertThat(counts.pending()).isEqualTo(1);
	}

	@Test
	void recentOrdersCarryTheBuyerAndTheSeatCountNewestFirst() {
		UUID venue = insertVenue("Main Hall");
		UUID section = insertSection(venue, "Orchestra", 1);
		UUID event = insertEvent(venue, "Opening Night", EventStatus.PUBLISHED);
		UUID first = insertEventSeat(event, insertSeat(section, "A", 1), EventSeatStatus.SOLD, "100.00");
		UUID second = insertEventSeat(event, insertSeat(section, "A", 2), EventSeatStatus.SOLD, "100.00");
		UUID buyer = insertUser(BUYER_EMAIL);

		UUID older = insertReservation(buyer, event, "CONFIRMED", NOW.plus(Duration.ofMinutes(5)));
		insertReservationItem(older, first, "100.00");
		insertOrder(older, buyer, OrderStatus.PAID, "100.00", "VND", NOW.minus(Duration.ofDays(2)));

		UUID newer = insertReservation(buyer, event, "CONFIRMED", NOW.plus(Duration.ofMinutes(5)));
		insertReservationItem(newer, second, "100.00");
		insertReservationItem(newer, first, "100.00");
		insertOrder(newer, buyer, OrderStatus.PAID, "200.00", "VND", NOW.minus(Duration.ofDays(1)));

		List<EventSalesRecentOrderResponse> orders = eventSalesMapper.findRecentOrders(event, 10);

		assertThat(orders).hasSize(2);
		assertThat(orders.getFirst().buyerEmail()).isEqualTo(BUYER_EMAIL);
		assertThat(orders.getFirst().seatCount()).isEqualTo(2);
		assertThat(orders.getFirst().totalAmount()).isEqualByComparingTo("200.00");
		assertThat(orders.getLast().seatCount()).isEqualTo(1);
	}

	@Test
	void recentOrdersRespectTheLimit() {
		UUID venue = insertVenue("Main Hall");
		UUID event = insertEvent(venue, "Opening Night", EventStatus.PUBLISHED);
		UUID buyer = insertUser(BUYER_EMAIL);
		for (int index = 0; index < 3; index++) {
			UUID reservation = insertReservation(buyer, event, "CONFIRMED", NOW.plus(Duration.ofMinutes(5)));
			insertOrder(reservation, buyer, OrderStatus.PAID, "100.00", "VND", NOW.minusSeconds(index));
		}

		assertThat(eventSalesMapper.findRecentOrders(event, 2)).hasSize(2);
	}

	@Test
	void ticketCountsSplitByStatus() {
		UUID venue = insertVenue("Main Hall");
		UUID section = insertSection(venue, "Orchestra", 1);
		UUID event = insertEvent(venue, "Opening Night", EventStatus.PUBLISHED);
		UUID first = insertEventSeat(event, insertSeat(section, "A", 1), EventSeatStatus.SOLD, "100.00");
		UUID second = insertEventSeat(event, insertSeat(section, "A", 2), EventSeatStatus.SOLD, "100.00");
		UUID buyer = insertUser(BUYER_EMAIL);
		UUID reservation = insertReservation(buyer, event, "CONFIRMED", NOW.plus(Duration.ofMinutes(5)));
		UUID order = insertOrder(reservation, buyer, OrderStatus.PAID, "200.00", "VND", NOW);
		insertTicket(order, first, TicketStatus.ACTIVE);
		insertTicket(order, second, TicketStatus.USED);

		EventSalesTicketsResponse tickets = eventSalesMapper.findTicketCounts(event);

		assertThat(tickets.issued()).isEqualTo(2);
		assertThat(tickets.active()).isEqualTo(1);
		assertThat(tickets.used()).isEqualTo(1);
		assertThat(tickets.cancelled()).isZero();
	}

	@Test
	void sectionsReportSeatSplitsAndSoldValueInDisplayOrder() {
		UUID venue = insertVenue("Main Hall");
		UUID orchestra = insertSection(venue, "Orchestra", 1);
		UUID balcony = insertSection(venue, "Balcony", 2);
		UUID event = insertEvent(venue, "Opening Night", EventStatus.PUBLISHED);
		insertEventSection(event, orchestra, "150.00", true);
		insertEventSection(event, balcony, "80.00", false);
		insertEventSeat(event, insertSeat(orchestra, "A", 1), EventSeatStatus.SOLD, "150.00");
		insertEventSeat(event, insertSeat(orchestra, "A", 2), EventSeatStatus.AVAILABLE, "150.00");
		insertEventSeat(event, insertSeat(balcony, "B", 1), EventSeatStatus.BLOCKED, "80.00");

		List<EventSalesSectionResponse> sections = eventSalesMapper.findSections(event);

		assertThat(sections).hasSize(2);
		assertThat(sections.getFirst().name()).isEqualTo("Orchestra");
		assertThat(sections.getFirst().price()).isEqualByComparingTo("150.00");
		assertThat(sections.getFirst().salesEnabled()).isTrue();
		assertThat(sections.getFirst().seatsTotal()).isEqualTo(2);
		assertThat(sections.getFirst().seatsSold()).isEqualTo(1);
		assertThat(sections.getFirst().seatsAvailable()).isEqualTo(1);
		assertThat(sections.getFirst().soldValue()).isEqualByComparingTo("150.00");
		assertThat(sections.getLast().name()).isEqualTo("Balcony");
		assertThat(sections.getLast().salesEnabled()).isFalse();
		assertThat(sections.getLast().seatsBlocked()).isEqualTo(1);
		assertThat(sections.getLast().soldValue()).isEqualByComparingTo("0");
	}

	@Test
	void heatmapRowsSplitSeatsByRowWithinEachSectionInDisplayOrder() {
		UUID venue = insertVenue("Main Hall");
		UUID orchestra = insertSection(venue, "Orchestra", 1);
		UUID balcony = insertSection(venue, "Balcony", 2);
		UUID event = insertEvent(venue, "Opening Night", EventStatus.PUBLISHED);
		insertEventSeat(event, insertSeat(orchestra, "A", 1), EventSeatStatus.SOLD, "100.00");
		insertEventSeat(event, insertSeat(orchestra, "A", 2), EventSeatStatus.SOLD, "100.00");
		insertEventSeat(event, insertSeat(orchestra, "B", 1), EventSeatStatus.AVAILABLE, "100.00");
		insertEventSeat(event, insertSeat(orchestra, "B", 2), EventSeatStatus.BLOCKED, "100.00");
		insertEventSeat(event, insertSeat(balcony, "C", 1), EventSeatStatus.AVAILABLE, "80.00");

		List<EventSalesHeatmapRecord> rows = eventSalesMapper.findHeatmapRows(event);

		assertThat(rows).hasSize(3);
		assertThat(rows.get(0).sectionName()).isEqualTo("Orchestra");
		assertThat(rows.get(0).rowLabel()).isEqualTo("A");
		assertThat(rows.get(0).seatsTotal()).isEqualTo(2);
		assertThat(rows.get(0).seatsSold()).isEqualTo(2);
		assertThat(rows.get(1).rowLabel()).isEqualTo("B");
		assertThat(rows.get(1).seatsAvailable()).isEqualTo(1);
		assertThat(rows.get(1).seatsBlocked()).isEqualTo(1);
		assertThat(rows.get(1).seatsSold()).isZero();
		// Display order decides which section comes first, not the row label.
		assertThat(rows.get(2).sectionName()).isEqualTo("Balcony");
		assertThat(rows.get(2).rowLabel()).isEqualTo("C");
	}

	@Test
	void heatmapRowsAreEmptyForAnEventWithoutInventory() {
		UUID venue = insertVenue("Main Hall");
		UUID event = insertEvent(venue, "Not Published Yet", EventStatus.DRAFT);

		assertThat(eventSalesMapper.findHeatmapRows(event)).isEmpty();
	}

	@Test
	void dailySalesGroupPaidOrdersByUtcDayAndIgnoreAnythingOlderThanTheWindow() {
		UUID venue = insertVenue("Main Hall");
		UUID section = insertSection(venue, "Orchestra", 1);
		UUID event = insertEvent(venue, "Opening Night", EventStatus.PUBLISHED);
		UUID seat = insertEventSeat(event, insertSeat(section, "A", 1), EventSeatStatus.SOLD, "100.00");
		UUID buyer = insertUser(BUYER_EMAIL);

		UUID inWindow = insertReservation(buyer, event, "CONFIRMED", NOW.plus(Duration.ofMinutes(5)));
		insertReservationItem(inWindow, seat, "100.00");
		insertOrder(inWindow, buyer, OrderStatus.PAID, "100.00", "VND", Instant.parse("2026-08-30T22:00:00Z"));

		UUID unpaid = insertReservation(buyer, event, "PENDING_PAYMENT", NOW.plus(Duration.ofMinutes(5)));
		insertOrder(unpaid, buyer, OrderStatus.PENDING, "100.00", "VND", Instant.parse("2026-08-30T23:00:00Z"));

		UUID tooOld = insertReservation(buyer, event, "CONFIRMED", NOW.plus(Duration.ofMinutes(5)));
		insertOrder(tooOld, buyer, OrderStatus.PAID, "100.00", "VND", Instant.parse("2026-07-01T10:00:00Z"));

		List<EventSalesDailyPointResponse> daily =
				eventSalesMapper.findDailySales(event, NOW.minus(Duration.ofDays(30)));

		assertThat(daily).hasSize(1);
		assertThat(daily.getFirst().date()).isEqualTo(LocalDate.parse("2026-08-30"));
		assertThat(daily.getFirst().paidOrders()).isEqualTo(1);
		assertThat(daily.getFirst().seatsSold()).isEqualTo(1);
	}

	private UUID insertVenue(String name) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO venues (id, name, address, city, country, timezone, status)
				VALUES (?, ?, '1 Event Street', 'Ho Chi Minh City', 'Vietnam', 'Asia/Ho_Chi_Minh', 'ACTIVE')
				""", id, name);
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

	private UUID insertEvent(UUID venueId, String name, EventStatus status) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO events (id, venue_id, name, start_time, sales_start_time, sales_end_time, status)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""",
				id,
				venueId,
				name,
				Timestamp.from(EVENT_START),
				Timestamp.from(SALES_START),
				Timestamp.from(SALES_END),
				status.name());
		return id;
	}

	private void insertEventSection(UUID eventId, UUID sectionId, String price, boolean salesEnabled) {
		jdbcTemplate.update("""
				INSERT INTO event_sections (id, event_id, venue_section_id, price, sales_enabled)
				VALUES (?, ?, ?, ?, ?)
				""", UUID.randomUUID(), eventId, sectionId, new BigDecimal(price), salesEnabled);
	}

	private UUID insertEventSeat(UUID eventId, UUID seatId, EventSeatStatus status, String price) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO event_seats (id, event_id, seat_id, price, permanent_status)
				VALUES (?, ?, ?, ?, ?)
				""", id, eventId, seatId, new BigDecimal(price), status.name());
		return id;
	}

	private UUID insertUser(String email) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO users (id, email, password_hash, role, status)
				VALUES (?, ?, 'password-hash', 'USER', 'ACTIVE')
				""", id, email);
		return id;
	}

	private UUID insertReservation(UUID userId, UUID eventId, String status, Instant expiresAt) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO reservations (
					id, user_id, event_id, hold_id, status, expires_at, total_amount, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, 100.00, ?, ?)
				""",
				id,
				userId,
				eventId,
				UUID.randomUUID(),
				status,
				Timestamp.from(expiresAt),
				Timestamp.from(NOW.minus(Duration.ofDays(3))),
				Timestamp.from(NOW.minus(Duration.ofDays(3))));
		return id;
	}

	private void insertReservationItem(UUID reservationId, UUID eventSeatId, String price) {
		jdbcTemplate.update("""
				INSERT INTO reservation_items (id, reservation_id, event_seat_id, price, created_at)
				VALUES (?, ?, ?, ?, ?)
				""",
				UUID.randomUUID(),
				reservationId,
				eventSeatId,
				new BigDecimal(price),
				Timestamp.from(NOW.minus(Duration.ofDays(3))));
	}

	private UUID insertOrder(
			UUID reservationId,
			UUID userId,
			OrderStatus status,
			String amount,
			String currency,
			Instant createdAt) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO orders (
					id, reservation_id, user_id, status, total_amount, currency, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""",
				id,
				reservationId,
				userId,
				status.name(),
				new BigDecimal(amount),
				currency,
				Timestamp.from(createdAt),
				Timestamp.from(createdAt));
		return id;
	}

	private void insertTicket(UUID orderId, UUID eventSeatId, TicketStatus status) {
		jdbcTemplate.update("""
				INSERT INTO tickets (
					id, order_id, event_seat_id, ticket_code, status, issued_at, used_at, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""",
				UUID.randomUUID(),
				orderId,
				eventSeatId,
				UUID.randomUUID().toString().repeat(2),
				status.name(),
				Timestamp.from(NOW),
				status == TicketStatus.USED ? Timestamp.from(NOW) : null,
				Timestamp.from(NOW));
	}
}
