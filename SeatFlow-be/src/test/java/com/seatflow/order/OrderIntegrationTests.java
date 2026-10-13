package com.seatflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.event.EventMapper;
import com.seatflow.event.EventRecord;
import com.seatflow.event.EventSeatMapper;
import com.seatflow.event.EventSeatRecord;
import com.seatflow.event.EventSectionMapper;
import com.seatflow.event.EventSectionRecord;
import com.seatflow.promo.PromoCodeCreateRequest;
import com.seatflow.promo.PromoCodeRejectedException;
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
class OrderIntegrationTests extends PostgresTestContainerSupport {

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
	private PromoCodeService promoCodeService;

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
	void createsOrderFromItemTotalAndDuplicateReturnsSameOrder() throws Exception {
		EventInventory inventory = insertEventInventory(2);
		UserRecord user = insertUser("buyer@example.com");
		ReservationRecord reservation = insertReservation(
				inventory,
				user,
				ReservationStatus.PENDING_PAYMENT,
				Instant.now().plusSeconds(60),
				List.of(new BigDecimal("125000.50"), new BigDecimal("85000.25")));

		MvcResult firstResult = createOrder(reservation.id(), user);
		JsonNode firstResponse = responseBody(firstResult);
		UUID orderId = UUID.fromString(firstResponse.get("id").asText());

		assertThat(firstResponse.get("status").asText()).isEqualTo("PENDING");
		assertThat(firstResponse.get("totalAmount").decimalValue()).isEqualByComparingTo("210000.75");
		assertThat(firstResponse.get("currency").asText()).isEqualTo("VND");
		assertThat(reservation.totalAmount()).isEqualByComparingTo("1.00");

		mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderRequest(reservation.id())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(orderId.toString()))
				.andExpect(jsonPath("$.totalAmount").value(210000.75));

		mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(orderId.toString()))
				.andExpect(jsonPath("$.reservationId").value(reservation.id().toString()));

		assertThat(countRows("orders")).isEqualTo(1);
		assertThat(orderMapper.findActiveByReservationAndUser(reservation.id(), user.id()).id())
				.isEqualTo(orderId);
	}

	@Test
	void foreignReservationAndOrderAreNotExposed() throws Exception {
		EventInventory inventory = insertEventInventory(1);
		UserRecord owner = insertUser("owner@example.com");
		UserRecord otherUser = insertUser("other@example.com");
		ReservationRecord reservation = insertReservation(
				inventory,
				owner,
				ReservationStatus.PENDING_PAYMENT,
				Instant.now().plusSeconds(60),
				List.of(new BigDecimal("125000.00")));

		mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(otherUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderRequest(reservation.id())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Reservation not found"));

		UUID orderId = UUID.fromString(responseBody(createOrder(reservation.id(), owner)).get("id").asText());
		mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(otherUser)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Order not found"));
		mockMvc.perform(get("/api/v1/users/me/orders")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(otherUser)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(0))
				.andExpect(jsonPath("$.totalItems").value(0));
	}

	@Test
	void expiredAndNonPendingReservationsAreRejected() throws Exception {
		EventInventory inventory = insertEventInventory(1);
		UserRecord user = insertUser("invalid@example.com");
		ReservationRecord expired = insertReservation(
				inventory,
				user,
				ReservationStatus.PENDING_PAYMENT,
				Instant.now().minusSeconds(1),
				List.of(new BigDecimal("125000.00")));

		mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderRequest(expired.id())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Order conflict"));

		ReservationRecord confirmed = insertReservation(
				inventory,
				user,
				ReservationStatus.CONFIRMED,
				Instant.now().plusSeconds(60),
				List.of(new BigDecimal("125000.00")));
		mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderRequest(confirmed.id())))
				.andExpect(status().isConflict());

		assertThat(countRows("orders")).isZero();
	}

	@Test
	void userHistoryIsPaginated() throws Exception {
		EventInventory inventory = insertEventInventory(1);
		UserRecord user = insertUser("history@example.com");
		for (int index = 0; index < 3; index++) {
			ReservationRecord reservation = insertReservation(
					inventory,
					user,
					ReservationStatus.PENDING_PAYMENT,
					Instant.now().plusSeconds(60),
					List.of(new BigDecimal("%d.00".formatted(100000 + index))));
			orderService.createOrder(user.id(), new OrderCreateRequest(reservation.id()));
		}

		mockMvc.perform(get("/api/v1/users/me/orders?page=1&size=2")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalItems").value(3))
				.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void concurrentCreationReturnsOneActiveOrder() throws Exception {
		EventInventory inventory = insertEventInventory(2);
		UserRecord user = insertUser("concurrent@example.com");
		ReservationRecord reservation = insertReservation(
				inventory,
				user,
				ReservationStatus.PENDING_PAYMENT,
				Instant.now().plusSeconds(60),
				List.of(new BigDecimal("125000.00"), new BigDecimal("85000.00")));
		OrderCreateRequest request = new OrderCreateRequest(reservation.id());
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<OrderResponse> first = executor.submit(() -> {
				ready.countDown();
				start.await();
				return orderService.createOrder(user.id(), request);
			});
			Future<OrderResponse> second = executor.submit(() -> {
				ready.countDown();
				start.await();
				return orderService.createOrder(user.id(), request);
			});
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			OrderResponse firstResponse = first.get(10, TimeUnit.SECONDS);
			OrderResponse secondResponse = second.get(10, TimeUnit.SECONDS);

			assertThat(secondResponse.id()).isEqualTo(firstResponse.id());
			assertThat(firstResponse.totalAmount()).isEqualByComparingTo("210000.00");
			assertThat(countRows("orders")).isEqualTo(1);
		}
		finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void promoCodeDiscountsTheOrderAndReplayDoesNotConsumeASecondUse() {
		EventInventory inventory = insertEventInventory(2);
		UserRecord user = insertUser("promo@example.com");
		promoCodeService.create(new PromoCodeCreateRequest(
				"save10", PromoDiscountType.PERCENT, new BigDecimal("10"), null, null, 1));
		ReservationRecord reservation = insertReservation(
				inventory,
				user,
				ReservationStatus.PENDING_PAYMENT,
				Instant.now().plusSeconds(60),
				List.of(new BigDecimal("125000"), new BigDecimal("75000")));

		OrderResponse first = orderService.createOrder(user.id(), new OrderCreateRequest(reservation.id(), " save10 "));
		OrderResponse replay = orderService.createOrder(user.id(), new OrderCreateRequest(reservation.id(), "SAVE10"));

		assertThat(first.promoCode()).isEqualTo("SAVE10");
		assertThat(first.discountAmount()).isEqualByComparingTo("20000");
		assertThat(first.totalAmount()).isEqualByComparingTo("180000");
		assertThat(replay.id()).isEqualTo(first.id());
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orders WHERE promo_code_id IS NOT NULL", Long.class)).isEqualTo(1L);
	}

	@Test
	void exhaustedPromoCodeIsRejectedAndFreedWhenTheOrderIsCancelled() {
		EventInventory inventory = insertEventInventory(2);
		UserRecord first = insertUser("first@example.com");
		UserRecord second = insertUser("second@example.com");
		promoCodeService.create(new PromoCodeCreateRequest(
				"ONCE1", PromoDiscountType.FIXED, new BigDecimal("5000"), null, null, 1));
		ReservationRecord firstReservation = insertReservation(inventory, first, ReservationStatus.PENDING_PAYMENT,
				Instant.now().plusSeconds(60), List.of(new BigDecimal("100000")));
		ReservationRecord secondReservation = insertReservation(inventory, second, ReservationStatus.PENDING_PAYMENT,
				Instant.now().plusSeconds(60), List.of(new BigDecimal("100000")));

		OrderResponse firstOrder = orderService.createOrder(first.id(), new OrderCreateRequest(firstReservation.id(), "ONCE1"));

		assertThat(firstOrder.totalAmount()).isEqualByComparingTo("95000");
		assertThatPromoRejected(second, secondReservation);

		jdbcTemplate.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ?", firstOrder.id());

		OrderResponse secondOrder = orderService.createOrder(second.id(), new OrderCreateRequest(secondReservation.id(), "ONCE1"));
		assertThat(secondOrder.discountAmount()).isEqualByComparingTo("5000");
	}

	@Test
	void concurrentCheckoutsCannotOverspendALimitedPromoCode() throws Exception {
		EventInventory inventory = insertEventInventory(3);
		promoCodeService.create(new PromoCodeCreateRequest(
				"RACE1", PromoDiscountType.FIXED, new BigDecimal("1000"), null, null, 1));
		int buyers = 3;
		List<UserRecord> users = new java.util.ArrayList<>();
		List<ReservationRecord> reservations = new java.util.ArrayList<>();
		for (int index = 0; index < buyers; index++) {
			UserRecord user = insertUser("racer%d@example.com".formatted(index));
			users.add(user);
			reservations.add(insertReservation(inventory, user, ReservationStatus.PENDING_PAYMENT,
					Instant.now().plusSeconds(60), List.of(new BigDecimal("50000"))));
		}
		CountDownLatch ready = new CountDownLatch(buyers);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(buyers);

		try {
			List<Future<Boolean>> results = new java.util.ArrayList<>();
			for (int index = 0; index < buyers; index++) {
				UserRecord user = users.get(index);
				ReservationRecord reservation = reservations.get(index);
				results.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					try {
						orderService.createOrder(user.id(), new OrderCreateRequest(reservation.id(), "RACE1"));
						return true;
					}
					catch (PromoCodeRejectedException ex) {
						return false;
					}
				}));
			}
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			long succeeded = 0;
			for (Future<Boolean> result : results) {
				if (result.get(15, TimeUnit.SECONDS)) {
					succeeded++;
				}
			}
			assertThat(succeeded).isEqualTo(1);
			assertThat(jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM orders WHERE promo_code_id IS NOT NULL", Long.class)).isEqualTo(1L);
		}
		finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	private void assertThatPromoRejected(UserRecord user, ReservationRecord reservation) {
		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> orderService.createOrder(user.id(), new OrderCreateRequest(reservation.id(), "ONCE1")))
				.isInstanceOf(PromoCodeRejectedException.class);
	}

	private MvcResult createOrder(UUID reservationId, UserRecord user) throws Exception {
		return mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderRequest(reservationId)))
				.andExpect(status().isCreated())
				.andReturn();
	}

	private JsonNode responseBody(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private ReservationRecord insertReservation(
			EventInventory inventory,
			UserRecord user,
			ReservationStatus status,
			Instant expiresAt,
			List<BigDecimal> itemPrices) {
		Instant createdAt = expiresAt.isAfter(Instant.now())
				? Instant.now().minusSeconds(60)
				: expiresAt.minusSeconds(60);
		ReservationRecord reservation = new ReservationRecord(
				UUID.randomUUID(),
				user.id(),
				inventory.event().id(),
				UUID.randomUUID(),
				status,
				expiresAt,
				new BigDecimal("1.00"),
				createdAt,
				createdAt);
		assertThat(reservationMapper.insert(reservation)).isEqualTo(1);

		List<ReservationItemRecord> items = java.util.stream.IntStream.range(0, itemPrices.size())
				.mapToObj(index -> new ReservationItemRecord(
						UUID.randomUUID(),
						reservation.id(),
						inventory.eventSeats().get(index).id(),
						itemPrices.get(index),
						createdAt))
				.toList();
		assertThat(reservationItemMapper.batchInsert(items)).isEqualTo(items.size());
		return reservationMapper.findByIdAndUser(reservation.id(), user.id());
	}

	private EventInventory insertEventInventory(int seatCount) {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(),
				"Order Hall %s".formatted(UUID.randomUUID()),
				"1 Order Street",
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
				"Order Event %s".formatted(UUID.randomUUID()),
				"Order integration test",
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

	private int countRows(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private void cleanDatabase() {
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

	private static String orderRequest(UUID reservationId) {
		return "{\"reservationId\":\"%s\"}".formatted(reservationId);
	}

	private record EventInventory(EventRecord event, List<EventSeatRecord> eventSeats) {
	}
}
