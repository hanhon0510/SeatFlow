package com.seatflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.event.EventMapper;
import com.seatflow.event.EventRecord;
import com.seatflow.event.EventSeatMapper;
import com.seatflow.event.EventSeatRecord;
import com.seatflow.event.EventSeatStatus;
import com.seatflow.event.EventSectionMapper;
import com.seatflow.event.EventSectionRecord;
import com.seatflow.idempotency.IdempotencyMapper;
import com.seatflow.idempotency.IdempotencyOperation;
import com.seatflow.idempotency.IdempotencyRecord;
import com.seatflow.order.OrderCreateRequest;
import com.seatflow.order.OrderMapper;
import com.seatflow.order.OrderResponse;
import com.seatflow.order.OrderService;
import com.seatflow.order.OrderStatus;
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
class PaymentIntegrationTests extends PostgresTestContainerSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JwtTokenService jwtTokenService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private PaymentMapper paymentMapper;

	@Autowired
	private PaymentIdempotencyService paymentIdempotencyService;

	@Autowired
	private IdempotencyMapper idempotencyMapper;

	@Autowired
	private OrderMapper orderMapper;

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

	@BeforeEach
	void setUp() {
		cleanDatabase();
	}

	@AfterEach
	void tearDown() {
		cleanDatabase();
	}

	@Test
	void successfulPaymentUsesDatabaseAmountAndSellsSeats() throws Exception {
		PaymentFixture fixture = insertFixture(
				"payment-success@example.com",
				List.of(new BigDecimal("125000.50"), new BigDecimal("85000.25")));

		MvcResult result = createPayment(fixture, "tok_success")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.amount").value(210000.75))
				.andExpect(jsonPath("$.failureReason").doesNotExist())
				.andReturn();
		UUID paymentId = UUID.fromString(responseBody(result).get("id").asText());

		PaymentRecord payment = paymentMapper.findById(paymentId);
		assertThat(payment.amount()).isEqualByComparingTo("210000.75");
		assertThat(payment.providerReference()).startsWith("sim_");
		assertThat(orderMapper.findByIdAndUser(fixture.order().id(), fixture.user().id()).status())
				.isEqualTo(OrderStatus.PAID);
		assertThat(reservationMapper.findByIdAndUser(fixture.reservation().id(), fixture.user().id()).status())
				.isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(eventSeatMapper.findByEventId(fixture.inventory().event().id()))
				.allSatisfy(seat -> {
					assertThat(seat.permanentStatus()).isEqualTo(EventSeatStatus.SOLD);
					assertThat(seat.version()).isEqualTo(1);
				});
		assertThat(countRows("payments")).isEqualTo(1);
	}

	@ParameterizedTest
	@CsvSource({
			"tok_declined, DECLINED, Payment declined",
			"tok_timeout, TIMED_OUT, Payment timed out",
			"tok_error, FAILED, Simulated provider error"
	})
	void failedPaymentOutcomesLeaveSeatsAvailable(
			String token,
			String expectedStatus,
			String expectedReason) throws Exception {
		PaymentFixture fixture = insertFixture(
				"payment-failure-%s@example.com".formatted(expectedStatus.toLowerCase()),
				List.of(new BigDecimal("125000.00")));

		createPayment(fixture, token)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value(expectedStatus))
				.andExpect(jsonPath("$.amount").value(125000.00))
				.andExpect(jsonPath("$.failureReason").value(expectedReason));

		assertThat(orderMapper.findByIdAndUser(fixture.order().id(), fixture.user().id()).status())
				.isEqualTo(OrderStatus.FAILED);
		assertThat(reservationMapper.findByIdAndUser(fixture.reservation().id(), fixture.user().id()).status())
				.isEqualTo(ReservationStatus.PAYMENT_FAILED);
		assertThat(eventSeatMapper.findByEventId(fixture.inventory().event().id()))
				.allSatisfy(seat -> {
					assertThat(seat.permanentStatus()).isEqualTo(EventSeatStatus.AVAILABLE);
					assertThat(seat.version()).isZero();
				});
	}

	@Test
	void foreignOrderIsNotExposed() throws Exception {
		PaymentFixture fixture = insertFixture(
				"payment-owner@example.com",
				List.of(new BigDecimal("125000.00")));
		UserRecord otherUser = insertUser("payment-other@example.com");

		mockMvc.perform(post("/api/v1/orders/{orderId}/payments", fixture.order().id())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(otherUser))
						.header("Idempotency-Key", "foreign-order-attempt")
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentRequest("tok_success")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Order not found"));

		assertThat(countRows("payments")).isZero();
		assertThat(orderMapper.findByIdAndUser(fixture.order().id(), fixture.user().id()).status())
				.isEqualTo(OrderStatus.PENDING);
	}

	@Test
	void paidOrderCannotBePaidAgain() throws Exception {
		PaymentFixture fixture = insertFixture(
				"payment-once@example.com",
				List.of(new BigDecimal("125000.00")));

		createPayment(fixture, "tok_success").andExpect(status().isCreated());
		createPayment(fixture, "tok_success")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Payment conflict"));

		assertThat(countRows("payments")).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = 'SUCCEEDED'",
				Integer.class,
				fixture.order().id())).isEqualTo(1);
	}

	@Test
	void sequentialDuplicateReturnsStoredResponse() throws Exception {
		PaymentFixture fixture = insertFixture(
				"payment-retry@example.com",
				List.of(new BigDecimal("125000.00")));
		String idempotencyKey = "sequential-payment-attempt";

		JsonNode firstResponse = responseBody(createPayment(fixture, "tok_success", idempotencyKey)
				.andExpect(status().isCreated())
				.andReturn());
		JsonNode secondResponse = responseBody(createPayment(fixture, "tok_success", idempotencyKey)
				.andExpect(status().isCreated())
				.andReturn());

		assertThat(secondResponse).isEqualTo(firstResponse);
		assertThat(countRows("payments")).isEqualTo(1);
		assertThat(countRows("idempotency_records")).isEqualTo(1);
		IdempotencyRecord record = idempotencyMapper.findByScope(
				fixture.user().id(),
				IdempotencyOperation.CREATE_PAYMENT,
				idempotencyKey);
		assertThat(record.responseStatus()).isEqualTo(201);
		assertThat(record.requestHash()).hasSize(64).doesNotContain("tok_success");
		assertThat(record.responseBody()).doesNotContain("tok_success");
	}

	@Test
	void concurrentDuplicateExecutesPaymentOnce() throws Exception {
		PaymentFixture fixture = insertFixture(
				"payment-concurrent-retry@example.com",
				List.of(new BigDecimal("125000.00")));
		String idempotencyKey = "concurrent-payment-attempt";
		PaymentCreateRequest request = new PaymentCreateRequest("tok_success");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<IdempotentPaymentResult> first = executor.submit(() -> {
				ready.countDown();
				start.await();
				return paymentIdempotencyService.createPayment(
						fixture.order().id(),
						fixture.user().id(),
						idempotencyKey,
						request);
			});
			Future<IdempotentPaymentResult> second = executor.submit(() -> {
				ready.countDown();
				start.await();
				return paymentIdempotencyService.createPayment(
						fixture.order().id(),
						fixture.user().id(),
						idempotencyKey,
						request);
			});
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			IdempotentPaymentResult firstResult = first.get(10, TimeUnit.SECONDS);
			IdempotentPaymentResult secondResult = second.get(10, TimeUnit.SECONDS);
			assertThat(secondResult).isEqualTo(firstResult);
			assertThat(countRows("payments")).isEqualTo(1);
			assertThat(countRows("idempotency_records")).isEqualTo(1);
		}
		finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void differentBodyWithSameKeyIsRejected() throws Exception {
		PaymentFixture fixture = insertFixture(
				"payment-key-conflict@example.com",
				List.of(new BigDecimal("125000.00")));
		String idempotencyKey = "conflicting-payment-attempt";

		createPayment(fixture, "tok_declined", idempotencyKey)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("DECLINED"));
		createPayment(fixture, "tok_error", idempotencyKey)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Idempotency key conflict"));

		assertThat(countRows("payments")).isEqualTo(1);
		assertThat(countRows("idempotency_records")).isEqualTo(1);
	}

	@Test
	void sameKeyIsScopedPerUser() throws Exception {
		PaymentFixture firstUser = insertFixture(
				"payment-scope-first@example.com",
				List.of(new BigDecimal("125000.00")));
		PaymentFixture secondUser = insertFixture(
				"payment-scope-second@example.com",
				List.of(new BigDecimal("85000.00")));
		String idempotencyKey = "shared-payment-attempt";

		createPayment(firstUser, "tok_success", idempotencyKey).andExpect(status().isCreated());
		createPayment(secondUser, "tok_success", idempotencyKey).andExpect(status().isCreated());

		assertThat(countRows("payments")).isEqualTo(2);
		assertThat(countRows("idempotency_records")).isEqualTo(2);
	}

	@Test
	void missingIdempotencyKeyIsRejectedWithoutCreatingRecords() throws Exception {
		PaymentFixture fixture = insertFixture(
				"payment-missing-key@example.com",
				List.of(new BigDecimal("125000.00")));

		mockMvc.perform(post("/api/v1/orders/{orderId}/payments", fixture.order().id())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.user()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentRequest("tok_success")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Idempotency-Key header is required"));

		assertThat(countRows("payments")).isZero();
		assertThat(countRows("idempotency_records")).isZero();
	}

	private org.springframework.test.web.servlet.ResultActions createPayment(
			PaymentFixture fixture,
			String token) throws Exception {
		return createPayment(fixture, token, UUID.randomUUID().toString());
	}

	private org.springframework.test.web.servlet.ResultActions createPayment(
			PaymentFixture fixture,
			String token,
			String idempotencyKey) throws Exception {
		return mockMvc.perform(post("/api/v1/orders/{orderId}/payments", fixture.order().id())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.user()))
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentRequest(token)));
	}

	private PaymentFixture insertFixture(String email, List<BigDecimal> prices) {
		EventInventory inventory = insertEventInventory(prices.size());
		UserRecord user = insertUser(email);
		ReservationRecord reservation = insertReservation(inventory, user, prices);
		OrderResponse order = orderService.createOrder(user.id(), new OrderCreateRequest(reservation.id()));
		return new PaymentFixture(inventory, user, reservation, order);
	}

	private ReservationRecord insertReservation(
			EventInventory inventory,
			UserRecord user,
			List<BigDecimal> prices) {
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

		List<ReservationItemRecord> items = java.util.stream.IntStream.range(0, prices.size())
				.mapToObj(index -> new ReservationItemRecord(
						UUID.randomUUID(),
						reservation.id(),
						inventory.eventSeats().get(index).id(),
						prices.get(index),
						now.minusSeconds(60)))
				.toList();
		assertThat(reservationItemMapper.batchInsert(items)).isEqualTo(items.size());
		return reservationMapper.findByIdAndUser(reservation.id(), user.id());
	}

	private EventInventory insertEventInventory(int seatCount) {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(),
				"Payment Hall %s".formatted(UUID.randomUUID()),
				"1 Payment Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh");
		venueMapper.insert(venue);
		VenueSectionRecord section = VenueSectionRecord.forInsert(UUID.randomUUID(), venue.id(), "Orchestra", 1);
		sectionMapper.insert(section);
		for (int seatNumber = 1; seatNumber <= seatCount; seatNumber++) {
			seatMapper.insert(SeatRecord.forInsert(
					UUID.randomUUID(),
					section.id(),
					"A",
					seatNumber,
					"A%s".formatted(seatNumber),
					false));
		}

		Instant now = Instant.now();
		EventRecord event = EventRecord.forInsert(
				UUID.randomUUID(),
				venue.id(),
				"Payment Event %s".formatted(UUID.randomUUID()),
				"Payment integration test",
				now.plus(Duration.ofDays(1)),
				now.minus(Duration.ofHours(1)),
				now.plus(Duration.ofHours(2)));
		eventMapper.insert(event);
		assertThat(eventSectionMapper.insertForDraftEvent(EventSectionRecord.forInsert(
				UUID.randomUUID(),
				event.id(),
				section.id(),
				new BigDecimal("125000.00"),
				true))).isEqualTo(1);
		assertThat(eventSeatMapper.insertForDraftEvent(event.id())).isEqualTo(seatCount);
		return new EventInventory(eventMapper.findById(event.id()), eventSeatMapper.findByEventId(event.id()));
	}

	private UserRecord insertUser(String email) {
		UserRecord user = UserRecord.forInsert(UUID.randomUUID(), email, "{bcrypt}hash", UserRole.USER);
		userMapper.insertWithRole(user);
		return userMapper.findById(user.id());
	}

	private String bearerToken(UserRecord user) {
		return "Bearer " + jwtTokenService.issueAccessToken(user).accessToken();
	}

	private JsonNode responseBody(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private int countRows(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM idempotency_records");
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

	private static String paymentRequest(String token) {
		return "{\"token\":\"%s\"}".formatted(token);
	}

	private record EventInventory(EventRecord event, List<EventSeatRecord> eventSeats) {
	}

	private record PaymentFixture(
			EventInventory inventory,
			UserRecord user,
			ReservationRecord reservation,
			OrderResponse order) {
	}
}
