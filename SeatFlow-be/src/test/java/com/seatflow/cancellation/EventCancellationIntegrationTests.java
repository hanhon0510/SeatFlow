package com.seatflow.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import com.seatflow.event.EventStateConflictException;
import com.seatflow.hold.SeatHoldRecord;
import com.seatflow.hold.SeatHoldStore;
import com.seatflow.order.OrderCreateRequest;
import com.seatflow.order.OrderResponse;
import com.seatflow.order.OrderService;
import com.seatflow.payment.PaymentConflictException;
import com.seatflow.payment.PaymentCreateRequest;
import com.seatflow.payment.PaymentService;
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
class EventCancellationIntegrationTests extends PostgresTestContainerSupport {

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
	private EventCancellationService cancellationService;

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
		admin = insertUser("admin@example.com", UserRole.ADMIN);
	}

	@AfterEach
	void tearDown() {
		cleanDatabase();
	}

	@Test
	void cancellingRefundsEveryPaidOrderAndClosesTheEvent() throws Exception {
		EventRecord event = insertEvent(Duration.ofDays(1));
		List<EventSeatRecord> seats = insertSeats(event, 3);
		OrderResponse first = pay(event, seats.subList(0, 2), "first@example.com", "125000", "75000");
		OrderResponse second = pay(event, seats.subList(2, 3), "second@example.com", "100000");

		mockMvc.perform(post("/api/v1/admin/events/{id}/cancel", event.id())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"  Venue flooded  \"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value(event.id().toString()))
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.reason").value("Venue flooded"))
				.andExpect(jsonPath("$.refundedOrders").value(2))
				.andExpect(jsonPath("$.remainingRefunds").value(0))
				.andExpect(jsonPath("$.ordersNeedingAttention").value(0));

		assertThat(string("SELECT status FROM events WHERE id = ?", event.id())).isEqualTo("CANCELLED");
		assertThat(strings("SELECT status FROM orders WHERE id IN (?, ?)", first.id(), second.id()))
				.containsOnly("REFUNDED");
		assertThat(strings("SELECT status FROM tickets")).hasSize(3).containsOnly("CANCELLED");
		assertThat(strings("SELECT reason FROM refunds")).containsOnly("Event cancelled: Venue flooded");
		assertThat(count("SELECT COUNT(*) FROM event_cancellations WHERE cancelled_by = ?", admin.id())).isEqualTo(1);
	}

	@Test
	void cancellingVoidsUnpaidCheckoutsAndNobodyCanPayForThemAfterwards() {
		EventRecord event = insertEvent(Duration.ofDays(1));
		List<EventSeatRecord> seats = insertSeats(event, 2);
		UserRecord buyer = insertUser("pending@example.com", UserRole.USER);
		ReservationRecord reservation = insertReservation(event, buyer, seats.subList(0, 1), "100000");
		OrderResponse pending = orderService.createOrder(buyer.id(), new OrderCreateRequest(reservation.id()));

		EventCancellationResponse response = cancellationService.cancelEvent(
				event.id(), admin.id(), EventCancellationRequest.NONE);

		assertThat(response.remainingRefunds()).isZero();
		assertThat(string("SELECT status FROM orders WHERE id = ?", pending.id())).isEqualTo("CANCELLED");
		assertThat(string("SELECT status FROM reservations WHERE id = ?", reservation.id())).isEqualTo("CANCELLED");
		assertThatThrownBy(() -> paymentService.createPayment(
				pending.id(), buyer.id(), new PaymentCreateRequest("tok_success")))
				.isInstanceOf(PaymentConflictException.class);
		assertThat(count("SELECT COUNT(*) FROM payments")).isZero();
	}

	@Test
	void aCancelledEventRefusesPaymentEvenForAnOrderThatWasStillOpen() {
		EventRecord event = insertEvent(Duration.ofDays(1));
		List<EventSeatRecord> seats = insertSeats(event, 1);
		UserRecord buyer = insertUser("late@example.com", UserRole.USER);
		ReservationRecord reservation = insertReservation(event, buyer, seats, "100000");
		OrderResponse pending = orderService.createOrder(buyer.id(), new OrderCreateRequest(reservation.id()));
		// The event is cancelled underneath an order that is still pending.
		jdbcTemplate.update("UPDATE events SET status = 'CANCELLED' WHERE id = ?", event.id());

		assertThatThrownBy(() -> paymentService.createPayment(
				pending.id(), buyer.id(), new PaymentCreateRequest("tok_success")))
				.isInstanceOf(PaymentConflictException.class);

		assertThat(count("SELECT COUNT(*) FROM payments")).isZero();
		assertThat(string("SELECT status FROM orders WHERE id = ?", pending.id())).isEqualTo("PENDING");
		assertThat(strings("SELECT permanent_status FROM event_seats")).containsOnly("AVAILABLE");
	}

	@Test
	void repeatingTheRequestChangesNothingAndRefundsNobodyTwice() {
		EventRecord event = insertEvent(Duration.ofDays(1));
		pay(event, insertSeats(event, 1), "repeat@example.com", "100000");
		EventCancellationResponse first = cancellationService.cancelEvent(
				event.id(), admin.id(), new EventCancellationRequest("Artist ill"));

		EventCancellationResponse second = cancellationService.cancelEvent(
				event.id(), admin.id(), new EventCancellationRequest("A different reason"));

		assertThat(second).isEqualTo(first);
		assertThat(second.reason()).isEqualTo("Artist ill");
		assertThat(count("SELECT COUNT(*) FROM refunds")).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM event_cancellations")).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE event_type = 'OrderRefunded'")).isEqualTo(1);
	}

	@Test
	void anEventThatHasAlreadyStartedCannotBeCancelled() {
		EventRecord event = insertEvent(Duration.ofDays(1));
		pay(event, insertSeats(event, 1), "started@example.com", "100000");
		jdbcTemplate.update("UPDATE events SET start_time = NOW() - INTERVAL '1 minute', sales_start_time = NOW() - INTERVAL '1 day', sales_end_time = NOW() - INTERVAL '2 minutes' WHERE id = ?", event.id());

		assertThatThrownBy(() -> cancellationService.cancelEvent(
				event.id(), admin.id(), EventCancellationRequest.NONE))
				.isInstanceOf(EventStateConflictException.class);

		assertThat(string("SELECT status FROM events WHERE id = ?", event.id())).isNotEqualTo("CANCELLED");
		assertThat(count("SELECT COUNT(*) FROM refunds")).isZero();
		assertThat(count("SELECT COUNT(*) FROM event_cancellations")).isZero();
	}

	@Test
	void ordersWithScannedTicketsAreReportedInsteadOfBeingRefunded() {
		EventRecord event = insertEvent(Duration.ofDays(1));
		List<EventSeatRecord> seats = insertSeats(event, 2);
		OrderResponse scanned = pay(event, seats.subList(0, 1), "scanned@example.com", "100000");
		OrderResponse untouched = pay(event, seats.subList(1, 2), "untouched@example.com", "100000");
		jdbcTemplate.update(
				"UPDATE tickets SET status = 'USED', used_at = NOW() WHERE order_id = ?", scanned.id());

		EventCancellationResponse response = cancellationService.cancelEvent(
				event.id(), admin.id(), EventCancellationRequest.NONE);

		assertThat(response.refundedOrders()).isEqualTo(1);
		assertThat(response.remainingRefunds()).isZero();
		assertThat(response.ordersNeedingAttention()).isEqualTo(1);
		assertThat(string("SELECT status FROM orders WHERE id = ?", scanned.id())).isEqualTo("PAID");
		assertThat(string("SELECT status FROM orders WHERE id = ?", untouched.id())).isEqualTo("REFUNDED");
	}

	@Test
	void concurrentCancellationsCancelOnceAndRefundEachOrderOnce() throws Exception {
		EventRecord event = insertEvent(Duration.ofDays(1));
		List<EventSeatRecord> seats = insertSeats(event, 3);
		for (int index = 0; index < 3; index++) {
			pay(event, seats.subList(index, index + 1), "racer%s@example.com".formatted(index), "100000");
		}
		int callers = 4;
		CountDownLatch ready = new CountDownLatch(callers);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(callers);

		try {
			List<Future<EventCancellationResponse>> results = new ArrayList<>();
			for (int index = 0; index < callers; index++) {
				results.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return cancellationService.cancelEvent(event.id(), admin.id(), EventCancellationRequest.NONE);
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			for (Future<EventCancellationResponse> result : results) {
				assertThat(result.get(30, TimeUnit.SECONDS).eventId()).isEqualTo(event.id());
			}
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(count("SELECT COUNT(*) FROM event_cancellations")).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM refunds")).isEqualTo(3);
		assertThat(strings("SELECT status FROM orders")).containsOnly("REFUNDED");
	}

	@Test
	void aPaymentRacingTheCancellationNeverLeavesAPaidOrderBehind() throws Exception {
		EventRecord event = insertEvent(Duration.ofDays(1));
		List<EventSeatRecord> seats = insertSeats(event, 1);
		UserRecord buyer = insertUser("racing@example.com", UserRole.USER);
		ReservationRecord reservation = insertReservation(event, buyer, seats, "100000");
		OrderResponse pending = orderService.createOrder(buyer.id(), new OrderCreateRequest(reservation.id()));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<?> payment = executor.submit(() -> {
				ready.countDown();
				start.await();
				try {
					paymentService.createPayment(
							pending.id(), buyer.id(), new PaymentCreateRequest("tok_success"));
				}
				catch (PaymentConflictException ex) {
					// Losing the race is a legitimate outcome.
				}
				return null;
			});
			Future<?> cancellation = executor.submit(() -> {
				ready.countDown();
				start.await();
				return cancellationService.cancelEvent(event.id(), admin.id(), EventCancellationRequest.NONE);
			});
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			payment.get(30, TimeUnit.SECONDS);
			cancellation.get(30, TimeUnit.SECONDS);
		}
		catch (ExecutionException ex) {
			throw new AssertionError("A racing call failed unexpectedly", ex.getCause());
		}
		finally {
			executor.shutdownNow();
		}

		// Whichever won, one more pass leaves nothing paid: a payment that landed first was refunded
		// by the cancellation, and one that lost never charged anybody.
		EventCancellationResponse settled = cancellationService.cancelEvent(
				event.id(), admin.id(), EventCancellationRequest.NONE);
		assertThat(settled.remainingRefunds()).isZero();
		assertThat(count("SELECT COUNT(*) FROM orders WHERE status = 'PAID'")).isZero();
		assertThat(count("SELECT COUNT(*) FROM payments WHERE status = 'SUCCEEDED'")).isZero();
		assertThat(strings("SELECT permanent_status FROM event_seats")).containsOnly("AVAILABLE");
	}

	@Test
	void onlyAdminsCanCancelAnEventAndUnknownEventsAreNotFound() throws Exception {
		EventRecord event = insertEvent(Duration.ofDays(1));
		UserRecord buyer = insertUser("buyer@example.com", UserRole.USER);

		mockMvc.perform(post("/api/v1/admin/events/{id}/cancel", event.id())
						.header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/admin/events/{id}/cancel", event.id()))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/admin/events/{id}/cancel", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));

		assertThat(string("SELECT status FROM events WHERE id = ?", event.id())).isNotEqualTo("CANCELLED");
	}

	@Test
	void aCancelledEventCanNoLongerBeEdited() throws Exception {
		EventRecord event = insertEvent(Duration.ofDays(1));
		cancellationService.cancelEvent(event.id(), admin.id(), EventCancellationRequest.NONE);

		mockMvc.perform(put("/api/v1/admin/events/{id}", event.id())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"venueId":"%s","name":"Renamed","startTime":"%s","salesStartTime":"%s","salesEndTime":"%s"}
								""".formatted(
								event.venueId(),
								event.startTime(),
								event.salesStartTime(),
								event.salesEndTime())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EVENT_STATE_CONFLICT"));

		assertThat(string("SELECT name FROM events WHERE id = ?", event.id())).isEqualTo(event.name());
	}

	@Test
	void anOverlongReasonIsRejected() throws Exception {
		EventRecord event = insertEvent(Duration.ofDays(1));

		mockMvc.perform(post("/api/v1/admin/events/{id}/cancel", event.id())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"%s\"}".formatted("x".repeat(201))))
				.andExpect(status().isBadRequest());

		assertThat(count("SELECT COUNT(*) FROM event_cancellations")).isZero();
	}

	private OrderResponse pay(
			EventRecord event, List<EventSeatRecord> seats, String email, String... prices) {
		UserRecord user = insertUser(email, UserRole.USER);
		ReservationRecord reservation = insertReservation(event, user, seats, prices);
		OrderResponse order = orderService.createOrder(user.id(), new OrderCreateRequest(reservation.id()));
		paymentService.createPayment(order.id(), user.id(), new PaymentCreateRequest("tok_success"));
		return order;
	}

	private ReservationRecord insertReservation(
			EventRecord event, UserRecord user, List<EventSeatRecord> seats, String... prices) {
		Instant now = Instant.now();
		ReservationRecord reservation = new ReservationRecord(
				UUID.randomUUID(),
				user.id(),
				event.id(),
				UUID.randomUUID(),
				ReservationStatus.PENDING_PAYMENT,
				now.plusSeconds(60),
				new BigDecimal("1.00"),
				now.minusSeconds(60),
				now.minusSeconds(60));
		assertThat(reservationMapper.insert(reservation)).isEqualTo(1);
		List<ReservationItemRecord> items = java.util.stream.IntStream.range(0, seats.size())
				.mapToObj(index -> new ReservationItemRecord(
						UUID.randomUUID(),
						reservation.id(),
						seats.get(index).id(),
						new BigDecimal(prices[index]),
						now.minusSeconds(60)))
				.toList();
		assertThat(reservationItemMapper.batchInsert(items)).isEqualTo(items.size());
		return reservationMapper.findByIdAndUser(reservation.id(), user.id());
	}

	private EventRecord insertEvent(Duration startsIn) {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(),
				"Cancellation Hall %s".formatted(UUID.randomUUID()),
				"1 Cancellation Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh");
		venueMapper.insert(venue);
		VenueSectionRecord section = VenueSectionRecord.forInsert(UUID.randomUUID(), venue.id(), "Orchestra", 1);
		sectionMapper.insert(section);
		Instant now = Instant.now();
		EventRecord event = EventRecord.forInsert(
				UUID.randomUUID(),
				venue.id(),
				"Cancellation Event %s".formatted(UUID.randomUUID()),
				"Cancellation integration test",
				now.plus(startsIn),
				now.minus(Duration.ofHours(1)),
				now.plus(Duration.ofHours(2)));
		eventMapper.insert(event);
		assertThat(eventSectionMapper.insertForDraftEvent(EventSectionRecord.forInsert(
				UUID.randomUUID(), event.id(), section.id(), new BigDecimal("125000.00"), true))).isEqualTo(1);
		return eventMapper.findById(event.id());
	}

	/** Adds seats to the event's venue section and materialises them as the event's inventory. */
	private List<EventSeatRecord> insertSeats(EventRecord event, int seatCount) {
		UUID sectionId = jdbcTemplate.queryForObject(
				"SELECT venue_section_id FROM event_sections WHERE event_id = ?", UUID.class, event.id());
		for (int seatNumber = 1; seatNumber <= seatCount; seatNumber++) {
			seatMapper.insert(SeatRecord.forInsert(
					UUID.randomUUID(), sectionId, "A", seatNumber, "A%s".formatted(seatNumber), false));
		}
		assertThat(eventSeatMapper.insertForDraftEvent(event.id())).isEqualTo(seatCount);
		return eventSeatMapper.findByEventId(event.id());
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

	private List<String> strings(String sql, Object... args) {
		return jdbcTemplate.queryForList(sql, String.class, args);
	}

	private int count(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

	private void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM event_cancellations");
		jdbcTemplate.update("DELETE FROM refunds");
		jdbcTemplate.update("DELETE FROM idempotency_records");
		jdbcTemplate.update("DELETE FROM tickets");
		jdbcTemplate.update("DELETE FROM outbox_events");
		jdbcTemplate.update("DELETE FROM payments");
		jdbcTemplate.update("DELETE FROM orders");
		jdbcTemplate.update("DELETE FROM promo_codes");
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
}
