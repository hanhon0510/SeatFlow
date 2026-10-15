package com.seatflow.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
import com.seatflow.hold.SeatHoldRecord;
import com.seatflow.hold.SeatHoldStore;
import com.seatflow.order.OrderCancellationService;
import com.seatflow.order.OrderConflictException;
import com.seatflow.order.OrderCreateRequest;
import com.seatflow.order.OrderResponse;
import com.seatflow.order.OrderService;
import com.seatflow.payment.PaymentConflictException;
import com.seatflow.payment.PaymentCreateRequest;
import com.seatflow.payment.PaymentService;
import com.seatflow.promo.PromoCodeCreateRequest;
import com.seatflow.promo.PromoCodeService;
import com.seatflow.promo.PromoDiscountType;
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
class RefundIntegrationTests extends PostgresTestContainerSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtTokenService jwtTokenService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderCancellationService orderCancellationService;

	@Autowired
	private PaymentService paymentService;

	@Autowired
	private RefundService refundService;

	@Autowired
	private PromoCodeService promoCodeService;

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
	void refundVoidsTicketsRestocksSeatsAndRecordsTheRefundAtomically() throws Exception {
		Paid paid = pay("buyer@example.com", "125000", "75000");

		mockMvc.perform(post("/api/v1/admin/orders/{id}/refund", paid.order().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"  Duplicate purchase  \"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.orderId").value(paid.order().id().toString()))
				.andExpect(jsonPath("$.orderStatus").value("REFUNDED"))
				.andExpect(jsonPath("$.amount").value(200000))
				.andExpect(jsonPath("$.currency").value("VND"))
				.andExpect(jsonPath("$.reason").value("Duplicate purchase"));

		assertThat(orderStatus(paid.order().id())).isEqualTo("REFUNDED");
		assertThat(string("SELECT status FROM payments WHERE order_id = ?", paid.order().id())).isEqualTo("REFUNDED");
		assertThat(strings("SELECT status FROM tickets WHERE order_id = ?", paid.order().id()))
				.hasSize(2).containsOnly("CANCELLED");
		assertThat(strings("SELECT permanent_status FROM event_seats WHERE event_id = ?", paid.event().id()))
				.containsOnly("AVAILABLE");
		assertThat(string("SELECT status FROM reservations WHERE id = ?", paid.reservation().id()))
				.isEqualTo("CANCELLED");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT refunded_by FROM refunds WHERE order_id = ?", UUID.class, paid.order().id()))
				.isEqualTo(admin.id());
		assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE event_type = 'OrderRefunded' AND aggregate_id = ?",
				paid.order().id())).isEqualTo(1);
	}

	@Test
	void repeatedRefundReturnsTheOriginalRefundAndChangesNothingElse() throws Exception {
		Paid paid = pay("repeat@example.com", "100000");
		RefundResponse first = refundService.refundOrder(paid.order().id(), admin.id(), new RefundRequest("first"));

		RefundResponse second = refundService.refundOrder(paid.order().id(), admin.id(), new RefundRequest("second"));

		assertThat(second.id()).isEqualTo(first.id());
		assertThat(second.reason()).isEqualTo("first");
		assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id = ?", paid.order().id())).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE event_type = 'OrderRefunded'")).isEqualTo(1);
	}

	@Test
	void concurrentRefundsOfOneOrderPayOutOnce() throws Exception {
		Paid paid = pay("race@example.com", "100000", "50000");
		int callers = 4;
		CountDownLatch ready = new CountDownLatch(callers);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(callers);

		try {
			List<Future<RefundResponse>> results = new java.util.ArrayList<>();
			for (int index = 0; index < callers; index++) {
				results.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return refundService.refundOrder(paid.order().id(), admin.id(), RefundRequest.NONE);
				}));
			}
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<UUID> refundIds = new java.util.ArrayList<>();
			for (Future<RefundResponse> result : results) {
				refundIds.add(result.get(20, TimeUnit.SECONDS).id());
			}
			assertThat(refundIds).containsOnly(refundIds.getFirst());
			assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id = ?", paid.order().id())).isEqualTo(1);
			assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE event_type = 'OrderRefunded'")).isEqualTo(1);
		}
		finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void onlyAdminsMayRefund() throws Exception {
		Paid paid = pay("owner@example.com", "100000");

		mockMvc.perform(post("/api/v1/admin/orders/{id}/refund", paid.order().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(paid.user())))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/admin/orders/{id}/refund", paid.order().id()))
				.andExpect(status().isUnauthorized());

		assertThat(orderStatus(paid.order().id())).isEqualTo("PAID");
	}

	@Test
	void unpaidAndUnknownOrdersCannotBeRefunded() throws Exception {
		Inventory inventory = insertInventory(1);
		UserRecord user = insertUser("pending@example.com", UserRole.USER);
		OrderResponse pending = orderService.createOrder(user.id(),
				new OrderCreateRequest(insertReservation(inventory, user, "100000").id()));

		mockMvc.perform(post("/api/v1/admin/orders/{id}/refund", pending.id())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORDER_NOT_REFUNDABLE"));
		mockMvc.perform(post("/api/v1/admin/orders/{id}/refund", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isNotFound());

		assertThat(orderStatus(pending.id())).isEqualTo("PENDING");
	}

	@Test
	void usedTicketBlocksTheRefundAndRollsBackEverything() throws Exception {
		Paid paid = pay("used@example.com", "100000", "50000");
		jdbcTemplate.update(
				"UPDATE tickets SET status = 'USED', used_at = issued_at WHERE id = (SELECT id FROM tickets WHERE order_id = ? LIMIT 1)",
				paid.order().id());

		assertThatThrownBy(() -> refundService.refundOrder(paid.order().id(), admin.id(), RefundRequest.NONE))
				.isInstanceOf(OrderNotRefundableException.class);

		assertThat(orderStatus(paid.order().id())).isEqualTo("PAID");
		assertThat(strings("SELECT permanent_status FROM event_seats WHERE event_id = ?", paid.event().id()))
				.containsOnly("SOLD");
		assertThat(count("SELECT COUNT(*) FROM refunds")).isZero();
	}

	@Test
	void refundedSeatsCanBeBoughtBySomeoneElse() throws Exception {
		Paid paid = pay("first-buyer@example.com", "100000");
		refundService.refundOrder(paid.order().id(), admin.id(), RefundRequest.NONE);

		UserRecord second = insertUser("second-buyer@example.com", UserRole.USER);
		ReservationRecord reservation = insertReservation(paid.inventory(), second, "100000");
		OrderResponse order = orderService.createOrder(second.id(), new OrderCreateRequest(reservation.id()));
		paymentService.createPayment(order.id(), second.id(), new PaymentCreateRequest("tok_success"));

		assertThat(orderStatus(order.id())).isEqualTo("PAID");
		assertThat(strings("SELECT status FROM tickets WHERE order_id = ?", order.id())).containsOnly("ACTIVE");
		assertThat(orderStatus(paid.order().id())).isEqualTo("REFUNDED");
	}

	@Test
	void refundGivesThePromoCodeUseBack() throws Exception {
		promoCodeService.create(new PromoCodeCreateRequest(
				"ONCE1", PromoDiscountType.FIXED, new BigDecimal("10000"), null, null, 1));
		Inventory inventory = insertInventory(1);
		UserRecord user = insertUser("promo-buyer@example.com", UserRole.USER);
		OrderResponse order = orderService.createOrder(user.id(),
				new OrderCreateRequest(insertReservation(inventory, user, "100000").id(), "ONCE1"));
		paymentService.createPayment(order.id(), user.id(), new PaymentCreateRequest("tok_success"));
		assertThat(order.totalAmount()).isEqualByComparingTo("90000");

		refundService.refundOrder(order.id(), admin.id(), RefundRequest.NONE);

		assertThat(count("SELECT COUNT(*) FROM refunds WHERE amount = 90000")).isEqualTo(1);
		assertThat(promoCodeService.list(0, 10).items().getFirst().usedCount()).isZero();
	}

	@Test
	void buyerCancelsAPendingOrderAndRepeatingItIsHarmless() throws Exception {
		Inventory inventory = insertInventory(1);
		UserRecord user = insertUser("canceller@example.com", UserRole.USER);
		ReservationRecord reservation = insertReservation(inventory, user, "100000");
		OrderResponse order = orderService.createOrder(user.id(), new OrderCreateRequest(reservation.id()));

		mockMvc.perform(post("/api/v1/orders/{id}/cancel", order.id()).header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		mockMvc.perform(post("/api/v1/orders/{id}/cancel", order.id()).header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		assertThat(string("SELECT status FROM reservations WHERE id = ?", reservation.id())).isEqualTo("CANCELLED");
		assertThatThrownBy(() -> paymentService.createPayment(order.id(), user.id(), new PaymentCreateRequest("tok_success")))
				.isInstanceOf(PaymentConflictException.class);
	}

	@Test
	void cancelRejectsOtherUsersAndPaidOrders() throws Exception {
		Paid paid = pay("paid-owner@example.com", "100000");
		UserRecord stranger = insertUser("stranger@example.com", UserRole.USER);

		mockMvc.perform(post("/api/v1/orders/{id}/cancel", paid.order().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/orders/{id}/cancel", paid.order().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(paid.user())))
				.andExpect(status().isConflict());

		assertThat(orderStatus(paid.order().id())).isEqualTo("PAID");
	}

	@Test
	void cancelRacingPaymentLeavesOneConsistentOutcome() throws Exception {
		Inventory inventory = insertInventory(1);
		UserRecord user = insertUser("racer@example.com", UserRole.USER);
		OrderResponse order = orderService.createOrder(user.id(),
				new OrderCreateRequest(insertReservation(inventory, user, "100000").id()));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<Boolean> payment = executor.submit(() -> {
				ready.countDown();
				start.await();
				try {
					paymentService.createPayment(order.id(), user.id(), new PaymentCreateRequest("tok_success"));
					return true;
				}
				catch (PaymentConflictException ex) {
					return false;
				}
			});
			Future<Boolean> cancellation = executor.submit(() -> {
				ready.countDown();
				start.await();
				try {
					orderCancellationService.cancelOrder(order.id(), user.id());
					return true;
				}
				catch (OrderConflictException ex) {
					return false;
				}
			});
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			boolean paid = payment.get(20, TimeUnit.SECONDS);
			boolean cancelled = cancellation.get(20, TimeUnit.SECONDS);

			assertThat(paid ^ cancelled).as("exactly one of pay and cancel wins").isTrue();
			if (paid) {
				assertThat(orderStatus(order.id())).isEqualTo("PAID");
				assertThat(strings("SELECT permanent_status FROM event_seats WHERE event_id = ?",
						inventory.event().id())).containsOnly("SOLD");
			}
			else {
				assertThat(orderStatus(order.id())).isEqualTo("CANCELLED");
				assertThat(count("SELECT COUNT(*) FROM tickets WHERE order_id = ?", order.id())).isZero();
				assertThat(strings("SELECT permanent_status FROM event_seats WHERE event_id = ?",
						inventory.event().id())).containsOnly("AVAILABLE");
			}
		}
		finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	private Paid pay(String email, String... prices) {
		Inventory inventory = insertInventory(prices.length);
		UserRecord user = insertUser(email, UserRole.USER);
		ReservationRecord reservation = insertReservation(inventory, user, prices);
		OrderResponse order = orderService.createOrder(user.id(), new OrderCreateRequest(reservation.id()));
		paymentService.createPayment(order.id(), user.id(), new PaymentCreateRequest("tok_success"));
		return new Paid(inventory, user, reservation, order);
	}

	private ReservationRecord insertReservation(Inventory inventory, UserRecord user, String... prices) {
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
		List<ReservationItemRecord> items = java.util.stream.IntStream.range(0, prices.length)
				.mapToObj(index -> new ReservationItemRecord(
						UUID.randomUUID(),
						reservation.id(),
						inventory.eventSeats().get(index).id(),
						new BigDecimal(prices[index]),
						now.minusSeconds(60)))
				.toList();
		assertThat(reservationItemMapper.batchInsert(items)).isEqualTo(items.size());
		return reservationMapper.findByIdAndUser(reservation.id(), user.id());
	}

	private Inventory insertInventory(int seatCount) {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(),
				"Refund Hall %s".formatted(UUID.randomUUID()),
				"1 Refund Street",
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
				"Refund Event %s".formatted(UUID.randomUUID()),
				"Refund integration test",
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

	private String orderStatus(UUID orderId) {
		return string("SELECT status FROM orders WHERE id = ?", orderId);
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

	private record Inventory(EventRecord event, List<EventSeatRecord> eventSeats) {
	}

	private record Paid(Inventory inventory, UserRecord user, ReservationRecord reservation, OrderResponse order) {

		EventRecord event() {
			return inventory.event();
		}
	}
}
