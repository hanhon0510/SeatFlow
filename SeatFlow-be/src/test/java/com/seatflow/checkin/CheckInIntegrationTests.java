package com.seatflow.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.event.EventMapper;
import com.seatflow.event.EventRecord;
import com.seatflow.event.EventSeatMapper;
import com.seatflow.event.EventSeatRecord;
import com.seatflow.event.EventSectionMapper;
import com.seatflow.event.EventSectionRecord;
import com.seatflow.hold.SeatHoldRecord;
import com.seatflow.hold.SeatHoldStore;
import com.seatflow.order.OrderCreateRequest;
import com.seatflow.order.OrderResponse;
import com.seatflow.order.OrderService;
import com.seatflow.payment.PaymentCreateRequest;
import com.seatflow.payment.PaymentService;
import com.seatflow.refund.OrderNotRefundableException;
import com.seatflow.refund.RefundRequest;
import com.seatflow.refund.RefundService;
import com.seatflow.reservation.ReservationItemMapper;
import com.seatflow.reservation.ReservationItemRecord;
import com.seatflow.reservation.ReservationMapper;
import com.seatflow.reservation.ReservationRecord;
import com.seatflow.reservation.ReservationStatus;
import com.seatflow.security.JwtTokenService;
import com.seatflow.seating.SeatMapper;
import com.seatflow.seating.SeatRecord;
import com.seatflow.seating.VenueSectionMapper;
import com.seatflow.seating.VenueSectionRecord;
import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.venue.VenueMapper;
import com.seatflow.venue.VenueRecord;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CheckInIntegrationTests extends PostgresTestContainerSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtTokenService jwtTokenService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private PaymentService paymentService;

	@Autowired
	private RefundService refundService;

	@Autowired
	private CheckInService checkInService;

	@Autowired
	private ReservationMapper reservationMapper;

	@Autowired
	private ReservationItemMapper reservationItemMapper;

	@Autowired
	private UserMapper userMapper;

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

	@MockitoBean
	private SeatHoldStore seatHoldStore;

	private UserRecord admin;

	@BeforeEach
	void setUp() {
		cleanDatabase();
		when(seatHoldStore.isHoldActive(any(SeatHoldRecord.class))).thenReturn(true);
		admin = insertUser("door-admin@example.com", UserRole.ADMIN);
	}

	@AfterEach
	void tearDown() {
		cleanDatabase();
	}

	@Test
	void firstScanAdmitsAndRecordsWhoAndWhenThenTheSecondIsRefused() throws Exception {
		Sale sale = sell("holder@example.com", 1);
		Ticket ticket = sale.tickets().getFirst();

		mockMvc.perform(scan(sale.inventory().event().id(), ticket.qrData()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("CHECKED_IN"))
				.andExpect(jsonPath("$.seatLabel").value("A1"))
				.andExpect(jsonPath("$.buyerEmail").value("holder@example.com"))
				.andExpect(jsonPath("$.usedAt").isNotEmpty());

		assertThat(string("SELECT status FROM tickets WHERE id = ?", ticket.id())).isEqualTo("USED");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT checked_in_by FROM tickets WHERE id = ?", UUID.class, ticket.id())).isEqualTo(admin.id());

		mockMvc.perform(scan(sale.inventory().event().id(), ticket.qrData()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("ALREADY_USED"))
				.andExpect(jsonPath("$.usedAt").isNotEmpty());
	}

	@Test
	void theBareCodeAdmitsJustLikeTheQr() throws Exception {
		Sale sale = sell("typed@example.com", 1);

		mockMvc.perform(scan(sale.inventory().event().id(), sale.tickets().getFirst().code()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("CHECKED_IN"));
	}

	@Test
	void concurrentScansOfOneTicketAdmitExactlyOnce() throws Exception {
		Sale sale = sell("crowd@example.com", 1);
		UUID eventId = sale.inventory().event().id();
		String qr = sale.tickets().getFirst().qrData();
		int scanners = 6;
		CountDownLatch ready = new CountDownLatch(scanners);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(scanners);

		try {
			List<Future<CheckInOutcome>> results = new ArrayList<>();
			for (int index = 0; index < scanners; index++) {
				results.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return checkInService.checkIn(eventId, admin.id(), qr).outcome();
				}));
			}
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<CheckInOutcome> outcomes = new ArrayList<>();
			for (Future<CheckInOutcome> result : results) {
				outcomes.add(result.get(20, TimeUnit.SECONDS));
			}
			assertThat(outcomes).filteredOn(outcome -> outcome == CheckInOutcome.CHECKED_IN).hasSize(1);
			assertThat(outcomes).filteredOn(outcome -> outcome == CheckInOutcome.ALREADY_USED).hasSize(scanners - 1);
		}
		finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void aRefundedTicketIsRefused() throws Exception {
		Sale sale = sell("refunded@example.com", 1);
		refundService.refundOrder(sale.order().id(), admin.id(), RefundRequest.NONE);

		mockMvc.perform(scan(sale.inventory().event().id(), sale.tickets().getFirst().qrData()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("CANCELLED"));

		assertThat(string("SELECT status FROM tickets WHERE id = ?", sale.tickets().getFirst().id()))
				.isEqualTo("CANCELLED");
	}

	@Test
	void anAdmittedTicketBlocksTheRefundOfItsOrder() throws Exception {
		Sale sale = sell("entered@example.com", 2);
		checkInService.checkIn(sale.inventory().event().id(), admin.id(), sale.tickets().getFirst().qrData());

		assertThatThrownBy(() -> refundService.refundOrder(sale.order().id(), admin.id(), RefundRequest.NONE))
				.isInstanceOf(OrderNotRefundableException.class);

		assertThat(string("SELECT status FROM orders WHERE id = ?", sale.order().id())).isEqualTo("PAID");
	}

	@Test
	void aTicketOfAnotherEventIsRefusedAndStaysUnused() throws Exception {
		Sale sale = sell("elsewhere@example.com", 1);
		Sale other = sell("other-event@example.com", 1);

		mockMvc.perform(scan(other.inventory().event().id(), sale.tickets().getFirst().qrData()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("WRONG_EVENT"))
				.andExpect(jsonPath("$.buyerEmail").doesNotExist());

		assertThat(string("SELECT status FROM tickets WHERE id = ?", sale.tickets().getFirst().id()))
				.isEqualTo("ACTIVE");
	}

	@Test
	void unknownAndMalformedScansAreNotFound() throws Exception {
		Sale sale = sell("known@example.com", 1);
		UUID eventId = sale.inventory().event().id();

		mockMvc.perform(scan(eventId, "seatflow:ticket:%s:%s".formatted(UUID.randomUUID(), "x".repeat(43))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("NOT_FOUND"));
		mockMvc.perform(scan(eventId, "not a ticket"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("NOT_FOUND"));
		// A real code with someone else's ticket id must not match either.
		mockMvc.perform(scan(eventId, "seatflow:ticket:%s:%s".formatted(UUID.randomUUID(), sale.tickets().getFirst().code())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("NOT_FOUND"));
	}

	@Test
	void summaryCountsValidTicketsAndListsTheLatestAdmissions() throws Exception {
		Sale sale = sell("summary@example.com", 3);
		UUID eventId = sale.inventory().event().id();
		checkInService.checkIn(eventId, admin.id(), sale.tickets().get(0).qrData());
		checkInService.checkIn(eventId, admin.id(), sale.tickets().get(1).qrData());
		jdbcTemplate.update("UPDATE tickets SET status = 'CANCELLED' WHERE id = ?", sale.tickets().get(2).id());

		mockMvc.perform(get("/api/v1/admin/events/{id}/check-in", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.valid").value(2))
				.andExpect(jsonPath("$.checkedIn").value(2))
				.andExpect(jsonPath("$.remaining").value(0))
				.andExpect(jsonPath("$.recent.length()").value(2))
				.andExpect(jsonPath("$.recent[0].buyerEmail").value("summary@example.com"));
	}

	@Test
	void onlyAdminsMayCheckPeopleIn() throws Exception {
		Sale sale = sell("guarded@example.com", 1);
		UUID eventId = sale.inventory().event().id();
		String qr = sale.tickets().getFirst().qrData();

		mockMvc.perform(post("/api/v1/admin/events/{id}/check-in", eventId)
						.header(HttpHeaders.AUTHORIZATION, bearer(sale.user()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"%s\"}".formatted(qr)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/admin/events/{id}/check-in", eventId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"%s\"}".formatted(qr)))
				.andExpect(status().isUnauthorized());

		assertThat(string("SELECT status FROM tickets WHERE id = ?", sale.tickets().getFirst().id()))
				.isEqualTo("ACTIVE");
	}

	@Test
	void blankCodeIsABadRequestAndUnknownEventIsNotFound() throws Exception {
		mockMvc.perform(post("/api/v1/admin/events/{id}/check-in", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"   \"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/admin/events/{id}/check-in", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"" + "x".repeat(43) + "\"}"))
				.andExpect(status().isNotFound());
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder scan(UUID eventId, String code) {
		return post("/api/v1/admin/events/{id}/check-in", eventId)
				.header(HttpHeaders.AUTHORIZATION, bearer(admin))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"%s\"}".formatted(code));
	}

	private Sale sell(String email, int seats) {
		Inventory inventory = insertInventory(seats);
		UserRecord user = insertUser(email, UserRole.USER);
		ReservationRecord reservation = insertReservation(inventory, user, seats);
		OrderResponse order = orderService.createOrder(user.id(), new OrderCreateRequest(reservation.id()));
		paymentService.createPayment(order.id(), user.id(), new PaymentCreateRequest("tok_success"));
		List<Ticket> tickets = jdbcTemplate.query(
				"SELECT id, ticket_code FROM tickets WHERE order_id = ? ORDER BY ticket_code",
				(rs, row) -> new Ticket(rs.getObject("id", UUID.class), rs.getString("ticket_code")),
				order.id());
		return new Sale(inventory, user, order, tickets);
	}

	private ReservationRecord insertReservation(Inventory inventory, UserRecord user, int seats) {
		Instant now = Instant.now();
		ReservationRecord reservation = new ReservationRecord(
				UUID.randomUUID(),
				user.id(),
				inventory.event().id(),
				UUID.randomUUID(),
				ReservationStatus.PENDING_PAYMENT,
				now.plusSeconds(60),
				new BigDecimal("1.00"),
				now.minusSeconds(60),
				now.minusSeconds(60));
		assertThat(reservationMapper.insert(reservation)).isEqualTo(1);
		List<ReservationItemRecord> items = IntStream.range(0, seats)
				.mapToObj(index -> new ReservationItemRecord(
						UUID.randomUUID(),
						reservation.id(),
						inventory.eventSeats().get(index).id(),
						new BigDecimal("100000"),
						now.minusSeconds(60)))
				.toList();
		assertThat(reservationItemMapper.batchInsert(items)).isEqualTo(items.size());
		return reservationMapper.findByIdAndUser(reservation.id(), user.id());
	}

	private Inventory insertInventory(int seatCount) {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(),
				"Door Hall %s".formatted(UUID.randomUUID()),
				"1 Door Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh");
		venueMapper.insert(venue);
		VenueSectionRecord section = VenueSectionRecord.forInsert(UUID.randomUUID(), venue.id(), "Orchestra", 1);
		sectionMapper.insert(section);
		for (int seatNumber = 1; seatNumber <= seatCount; seatNumber++) {
			seatMapper.insert(SeatRecord.forInsert(
					UUID.randomUUID(), section.id(), "A", seatNumber, "A%s".formatted(seatNumber), false));
		}
		Instant now = Instant.now();
		EventRecord event = EventRecord.forInsert(
				UUID.randomUUID(),
				venue.id(),
				"Door Event %s".formatted(UUID.randomUUID()),
				"Check-in integration test",
				now.plus(Duration.ofDays(1)),
				now.minus(Duration.ofHours(1)),
				now.plus(Duration.ofHours(2)));
		eventMapper.insert(event);
		assertThat(eventSectionMapper.insertForDraftEvent(EventSectionRecord.forInsert(
				UUID.randomUUID(), event.id(), section.id(), new BigDecimal("125000.00"), true))).isEqualTo(1);
		assertThat(eventSeatMapper.insertForDraftEvent(event.id())).isEqualTo(seatCount);
		return new Inventory(eventMapper.findById(event.id()), eventSeatMapper.findByEventId(event.id()));
	}

	private UserRecord insertUser(String email, UserRole role) {
		UserRecord user = UserRecord.forInsert(UUID.randomUUID(), email, "{bcrypt}hash", role);
		userMapper.insertWithRole(user);
		return userMapper.findById(user.id());
	}

	private String bearer(UserRecord user) {
		return "Bearer " + jwtTokenService.issueAccessToken(user).accessToken();
	}

	private String string(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, String.class, args);
	}

	private void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM refunds");
		jdbcTemplate.update("DELETE FROM idempotency_records");
		jdbcTemplate.update("DELETE FROM tickets");
		jdbcTemplate.update("DELETE FROM outbox_events");
		jdbcTemplate.update("DELETE FROM payments");
		jdbcTemplate.update("DELETE FROM orders");
		jdbcTemplate.update("DELETE FROM reservation_items");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM refresh_tokens");
		jdbcTemplate.update("DELETE FROM event_seats");
		jdbcTemplate.update("DELETE FROM event_sections");
		jdbcTemplate.update("DELETE FROM events");
		jdbcTemplate.update("DELETE FROM seats");
		jdbcTemplate.update("DELETE FROM venue_sections");
		jdbcTemplate.update("DELETE FROM venues");
		jdbcTemplate.update("DELETE FROM users");
	}

	private record Inventory(EventRecord event, List<EventSeatRecord> eventSeats) {
	}

	private record Ticket(UUID id, String code) {

		String qrData() {
			return "seatflow:ticket:%s:%s".formatted(id, code);
		}
	}

	private record Sale(Inventory inventory, UserRecord user, OrderResponse order, List<Ticket> tickets) {
	}
}
