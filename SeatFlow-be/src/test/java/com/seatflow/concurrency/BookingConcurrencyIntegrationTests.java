package com.seatflow.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import com.seatflow.hold.SeatHoldRedisKeys;
import com.seatflow.order.OrderCreateRequest;
import com.seatflow.order.OrderResponse;
import com.seatflow.order.OrderService;
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
import com.seatflow.support.RedisTestContainerSupport;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.venue.VenueMapper;
import com.seatflow.venue.VenueRecord;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class BookingConcurrencyIntegrationTests extends RedisTestContainerSupport {

	private static final int RACE_REPETITIONS = 3;
	private static final int ONE_SEAT_HOLD_USERS = 100;
	private static final int PAYMENT_ATTEMPTS = 20;
	private static final BigDecimal SEAT_PRICE = new BigDecimal("125000.00");
	private static final String HOLD_DATA_KEY_PATTERN = "seatflow:hold:data:*";
	private static final String HOLD_USER_KEY_PATTERN = "seatflow:hold:user:*";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private JwtTokenService jwtTokenService;

	@Autowired
	private OrderService orderService;

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

	@Autowired
	private ReservationMapper reservationMapper;

	@Autowired
	private ReservationItemMapper reservationItemMapper;

	@BeforeEach
	void setUp() {
		cleanDatabaseAndRedis();
	}

	@AfterEach
	void tearDown() {
		cleanDatabaseAndRedis();
	}

	@RepeatedTest(RACE_REPETITIONS)
	void oneHundredUsersAttemptOneSeatAndOnlyOneActiveHoldWins() throws Exception {
		PublishedInventory inventory = insertPublishedInventory(1);
		EventSeatRecord eventSeat = inventory.eventSeats().getFirst();
		List<UserRecord> users = IntStream.range(0, ONE_SEAT_HOLD_USERS)
				.mapToObj(index -> insertUser("hold-race-%03d-%s@example.com".formatted(index, UUID.randomUUID())))
				.toList();

		List<ApiAttempt> attempts = runConcurrently(
				ONE_SEAT_HOLD_USERS,
				index -> createHold(inventory.event(), users.get(index), List.of(eventSeat.id())));

		List<ApiAttempt> winners = attempts.stream()
				.filter(ApiAttempt::created)
				.toList();
		List<ApiAttempt> conflicts = attempts.stream()
				.filter(attempt -> attempt.status() == 409)
				.toList();

		assertThat(winners).singleElement()
				.satisfies(winner -> {
					assertThat(winner.body().get("eventId").asText()).isEqualTo(inventory.event().id().toString());
					assertThat(winner.body().get("eventSeatIds")).hasSize(1);
					assertThat(winner.body().get("eventSeatIds").get(0).asText()).isEqualTo(eventSeat.id().toString());
				});
		assertThat(conflicts).hasSize(ONE_SEAT_HOLD_USERS - 1)
				.allSatisfy(attempt -> assertThat(attempt.body().get("title").asText())
						.isEqualTo("Seat hold conflict"));

		ApiAttempt winner = winners.getFirst();
		UUID holdId = uuid(winner.body(), "holdId");
		UUID winnerUserId = uuid(winner.body(), "userId");
		assertRedisHoldExists(inventory.event().id(), List.of(eventSeat.id()), holdId, winnerUserId);
		assertThat(redisKeys(HOLD_DATA_KEY_PATTERN)).containsExactlyInAnyOrder(SeatHoldRedisKeys.data(holdId));
		assertThat(redisKeys(HOLD_USER_KEY_PATTERN)).containsExactlyInAnyOrder(SeatHoldRedisKeys.user(winnerUserId));

		assertThat(countRows("reservations")).isZero();
		assertThat(countRows("orders")).isZero();
		assertThat(countRows("payments")).isZero();
		assertThat(eventSeatStatus(eventSeat.id())).isEqualTo(EventSeatStatus.AVAILABLE.name());
	}

	@RepeatedTest(RACE_REPETITIONS)
	void overlappingMultiSeatRequestsProduceOneCompleteWinnerAndNoPartialHold() throws Exception {
		PublishedInventory inventory = insertPublishedInventory(3);
		List<EventSeatRecord> eventSeats = inventory.eventSeats();
		List<UUID> firstRequest = List.of(eventSeats.get(0).id(), eventSeats.get(1).id());
		List<UUID> secondRequest = List.of(eventSeats.get(1).id(), eventSeats.get(2).id());
		UserRecord firstUser = insertUser("overlap-first-%s@example.com".formatted(UUID.randomUUID()));
		UserRecord secondUser = insertUser("overlap-second-%s@example.com".formatted(UUID.randomUUID()));

		List<ApiAttempt> attempts = runConcurrently(2, index -> {
			if (index == 0) {
				return createHold(inventory.event(), firstUser, firstRequest);
			}
			return createHold(inventory.event(), secondUser, secondRequest);
		});

		List<ApiAttempt> winners = attempts.stream()
				.filter(ApiAttempt::created)
				.toList();
		List<ApiAttempt> conflicts = attempts.stream()
				.filter(attempt -> attempt.status() == 409)
				.toList();

		assertThat(winners).hasSize(1);
		assertThat(conflicts).singleElement()
				.satisfies(attempt -> assertThat(attempt.body().get("title").asText())
						.isEqualTo("Seat hold conflict"));

		ApiAttempt winner = winners.getFirst();
		UUID holdId = uuid(winner.body(), "holdId");
		UUID winnerUserId = uuid(winner.body(), "userId");
		List<UUID> winnerSeatIds = eventSeatIds(winner);
		assertThat(List.of(firstRequest, secondRequest)).anyMatch(winnerSeatIds::equals);
		assertThat(winnerSeatIds).hasSize(2);

		for (EventSeatRecord eventSeat : eventSeats) {
			String seatKeyValue = redisTemplate.opsForValue()
					.get(SeatHoldRedisKeys.seat(inventory.event().id(), eventSeat.id()));
			if (winnerSeatIds.contains(eventSeat.id())) {
				assertThat(seatKeyValue).isEqualTo(holdId.toString());
			}
			else {
				assertThat(seatKeyValue).isNull();
			}
		}
		assertThat(redisKeys(HOLD_DATA_KEY_PATTERN)).containsExactlyInAnyOrder(SeatHoldRedisKeys.data(holdId));
		assertThat(redisKeys(HOLD_USER_KEY_PATTERN)).containsExactlyInAnyOrder(SeatHoldRedisKeys.user(winnerUserId));

		assertThat(countRows("reservations")).isZero();
		assertThat(eventSeats.stream().map(EventSeatRecord::id).map(this::eventSeatStatus).toList())
				.containsOnly(EventSeatStatus.AVAILABLE.name());
	}

	@RepeatedTest(RACE_REPETITIONS)
	void manyPaymentRequestsWithOneIdempotencyKeyCreateOnePayment() throws Exception {
		PublishedInventory inventory = insertPublishedInventory(1);
		EventSeatRecord eventSeat = inventory.eventSeats().getFirst();
		UserRecord user = insertUser("payment-key-race-%s@example.com".formatted(UUID.randomUUID()));
		Checkout checkout = createApiCheckout(inventory, user, List.of(eventSeat.id()));
		assertRedisHoldExists(inventory.event().id(), List.of(eventSeat.id()), checkout.holdId(), user.id());
		String idempotencyKey = "payment-key-race-" + UUID.randomUUID();

		List<ApiAttempt> attempts = runConcurrently(
				PAYMENT_ATTEMPTS,
				ignored -> createPayment(checkout.orderId(), user, idempotencyKey));

		assertThat(attempts).hasSize(PAYMENT_ATTEMPTS)
				.allSatisfy(attempt -> {
					assertThat(attempt.status()).isEqualTo(201);
					assertThat(attempt.body().get("status").asText()).isEqualTo("SUCCEEDED");
				});
		assertThat(attempts.stream().map(attempt -> attempt.body().get("id").asText()).distinct().toList())
				.hasSize(1);

		assertThat(countRows("payments")).isEqualTo(1);
		assertThat(countRows("idempotency_records")).isEqualTo(1);
		assertThat(countRows("tickets")).isEqualTo(1);
		assertThat(countRows("outbox_events")).isEqualTo(1);
		assertThat(orderStatus(checkout.orderId())).isEqualTo("PAID");
		assertThat(reservationStatus(checkout.reservationId())).isEqualTo("CONFIRMED");
		assertThat(eventSeatStatus(eventSeat.id())).isEqualTo(EventSeatStatus.SOLD.name());
		assertRedisHoldReleased(inventory.event().id(), List.of(eventSeat.id()), checkout.holdId(), user.id());
	}

	@RepeatedTest(RACE_REPETITIONS)
	void twoPurchaseTransactionsForOneSeatConfirmOneBuyerAndOneSoldSeatOwner() throws Exception {
		PublishedInventory inventory = insertPublishedInventory(1);
		EventSeatRecord eventSeat = inventory.eventSeats().getFirst();
		UserRecord activeHolder = insertUser("purchase-winner-%s@example.com".formatted(UUID.randomUUID()));
		UserRecord competingBuyer = insertUser("purchase-loser-%s@example.com".formatted(UUID.randomUUID()));
		Checkout activeCheckout = createApiCheckout(inventory, activeHolder, List.of(eventSeat.id()));
		Checkout competingCheckout = insertPendingCheckout(inventory, competingBuyer, List.of(eventSeat.id()));
		assertRedisHoldExists(inventory.event().id(), List.of(eventSeat.id()), activeCheckout.holdId(), activeHolder.id());

		List<PurchaseCommand> commands = List.of(
				new PurchaseCommand(activeCheckout, activeHolder, "active-purchase-" + UUID.randomUUID()),
				new PurchaseCommand(competingCheckout, competingBuyer, "competing-purchase-" + UUID.randomUUID()));
		List<ApiAttempt> attempts = runConcurrently(
				commands.size(),
				index -> createPayment(
						commands.get(index).checkout().orderId(),
						commands.get(index).user(),
						commands.get(index).idempotencyKey()));

		List<ApiAttempt> successful = attempts.stream()
				.filter(ApiAttempt::created)
				.toList();
		List<ApiAttempt> conflicts = attempts.stream()
				.filter(attempt -> attempt.status() == 409)
				.toList();

		assertThat(successful).singleElement()
				.satisfies(attempt -> assertThat(attempt.body().get("status").asText()).isEqualTo("SUCCEEDED"));
		assertThat(conflicts).singleElement()
				.satisfies(attempt -> assertThat(attempt.body().get("title").asText()).isEqualTo("Payment conflict"));

		assertThat(countRows("payments")).isEqualTo(1);
		assertThat(countRows("idempotency_records")).isEqualTo(1);
		assertThat(countRows("tickets")).isEqualTo(1);
		assertThat(countRows("outbox_events")).isEqualTo(1);
		assertThat(orderStatus(activeCheckout.orderId())).isEqualTo("PAID");
		assertThat(reservationStatus(activeCheckout.reservationId())).isEqualTo("CONFIRMED");
		assertThat(orderStatus(competingCheckout.orderId())).isEqualTo("PENDING");
		assertThat(reservationStatus(competingCheckout.reservationId())).isEqualTo("PENDING_PAYMENT");
		assertThat(eventSeatStatus(eventSeat.id())).isEqualTo(EventSeatStatus.SOLD.name());
		assertThat(ticketOwnerIds()).containsExactly(activeHolder.id());
		assertRedisHoldReleased(inventory.event().id(), List.of(eventSeat.id()), activeCheckout.holdId(), activeHolder.id());
	}

	private Checkout createApiCheckout(
			PublishedInventory inventory,
			UserRecord user,
			List<UUID> eventSeatIds) throws Exception {
		ApiAttempt hold = createHold(inventory.event(), user, eventSeatIds);
		assertThat(hold.status()).isEqualTo(201);
		UUID holdId = uuid(hold.body(), "holdId");

		ApiAttempt reservation = createReservation(holdId, user);
		assertThat(reservation.status()).isEqualTo(201);
		UUID reservationId = uuid(reservation.body(), "id");

		ApiAttempt order = createOrder(reservationId, user);
		assertThat(order.status()).isEqualTo(201);
		UUID orderId = uuid(order.body(), "id");
		return new Checkout(holdId, reservationId, orderId);
	}

	private Checkout insertPendingCheckout(
			PublishedInventory inventory,
			UserRecord user,
			List<UUID> eventSeatIds) {
		Instant now = Instant.now();
		UUID holdId = UUID.randomUUID();
		BigDecimal totalAmount = SEAT_PRICE.multiply(BigDecimal.valueOf(eventSeatIds.size()));
		ReservationRecord reservation = ReservationRecord.pending(
				UUID.randomUUID(),
				user.id(),
				inventory.event().id(),
				holdId,
				now.plusSeconds(120),
				totalAmount,
				now);
		assertThat(reservationMapper.insert(reservation)).isEqualTo(1);
		List<ReservationItemRecord> items = eventSeatIds.stream()
				.map(eventSeatId -> new ReservationItemRecord(
						UUID.randomUUID(),
						reservation.id(),
						eventSeatId,
						SEAT_PRICE,
						now))
				.toList();
		assertThat(reservationItemMapper.batchInsert(items)).isEqualTo(items.size());
		OrderResponse order = orderService.createOrder(user.id(), new OrderCreateRequest(reservation.id()));
		return new Checkout(holdId, reservation.id(), order.id());
	}

	private ApiAttempt createHold(EventRecord event, UserRecord user, List<UUID> eventSeatIds) throws Exception {
		return apiAttempt(mockMvc.perform(post("/api/v1/events/{eventId}/holds", event.id())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content(holdRequest(eventSeatIds)))
				.andReturn());
	}

	private ApiAttempt createReservation(UUID holdId, UserRecord user) throws Exception {
		return apiAttempt(mockMvc.perform(post("/api/v1/reservations")
				.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"holdId\":\"%s\"}".formatted(holdId)))
				.andReturn());
	}

	private ApiAttempt createOrder(UUID reservationId, UserRecord user) throws Exception {
		return apiAttempt(mockMvc.perform(post("/api/v1/orders")
				.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reservationId\":\"%s\"}".formatted(reservationId)))
				.andReturn());
	}

	private ApiAttempt createPayment(UUID orderId, UserRecord user, String idempotencyKey) throws Exception {
		return apiAttempt(mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
				.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"tok_success\"}"))
				.andReturn());
	}

	private PublishedInventory insertPublishedInventory(int seatCount) {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(),
				"Concurrency Hall %s".formatted(UUID.randomUUID()),
				"1 Race Street",
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
				"Concurrency Event %s".formatted(UUID.randomUUID()),
				"Booking concurrency integration test",
				now.plus(Duration.ofDays(1)),
				now.minus(Duration.ofHours(1)),
				now.plus(Duration.ofHours(2)));
		eventMapper.insert(event);
		assertThat(eventSectionMapper.insertForDraftEvent(EventSectionRecord.forInsert(
				UUID.randomUUID(),
				event.id(),
				section.id(),
				SEAT_PRICE,
				true))).isEqualTo(1);
		assertThat(eventSeatMapper.insertForDraftEvent(event.id())).isEqualTo(seatCount);
		assertThat(eventMapper.publishDraft(event.id())).isEqualTo(1);
		return new PublishedInventory(eventMapper.findById(event.id()), eventSeatMapper.findByEventId(event.id()));
	}

	private UserRecord insertUser(String email) {
		UserRecord user = UserRecord.forInsert(UUID.randomUUID(), email, "{bcrypt}hash", UserRole.USER);
		userMapper.insertWithRole(user);
		return userMapper.findById(user.id());
	}

	private <T> List<T> runConcurrently(int participants, ConcurrentAction<T> action) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(participants);
		CyclicBarrier barrier = new CyclicBarrier(participants);
		List<Future<T>> futures = IntStream.range(0, participants)
				.mapToObj(index -> executor.submit(() -> {
					awaitBarrier(barrier);
					return action.run(index);
				}))
				.toList();

		try {
			List<T> results = new ArrayList<>(participants);
			for (Future<T> future : futures) {
				results.add(getFuture(future));
			}
			return results;
		}
		finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}

	private static void awaitBarrier(CyclicBarrier barrier)
			throws InterruptedException, BrokenBarrierException, TimeoutException {
		barrier.await(20, TimeUnit.SECONDS);
	}

	private static <T> T getFuture(Future<T> future) throws Exception {
		try {
			return future.get(60, TimeUnit.SECONDS);
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof Exception exception) {
				throw exception;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException(cause);
		}
	}

	private void assertRedisHoldExists(UUID eventId, List<UUID> eventSeatIds, UUID holdId, UUID userId) {
		for (UUID eventSeatId : eventSeatIds) {
			assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(eventId, eventSeatId)))
					.isEqualTo(holdId.toString());
		}
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.data(holdId))).isNotBlank();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.user(userId))).isEqualTo(holdId.toString());
	}

	private void assertRedisHoldReleased(UUID eventId, List<UUID> eventSeatIds, UUID holdId, UUID userId) {
		for (UUID eventSeatId : eventSeatIds) {
			assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(eventId, eventSeatId))).isNull();
		}
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.data(holdId))).isNull();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.user(userId))).isNull();
	}

	private ApiAttempt apiAttempt(MvcResult result) throws Exception {
		String content = result.getResponse().getContentAsString();
		JsonNode body = content == null || content.isBlank()
				? objectMapper.nullNode()
				: objectMapper.readTree(content);
		return new ApiAttempt(result.getResponse().getStatus(), body);
	}

	private String bearerToken(UserRecord user) {
		return "Bearer " + jwtTokenService.issueAccessToken(user).accessToken();
	}

	private String eventSeatStatus(UUID eventSeatId) {
		return jdbcTemplate.queryForObject(
				"SELECT permanent_status FROM event_seats WHERE id = ?",
				String.class,
				eventSeatId);
	}

	private String orderStatus(UUID orderId) {
		return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
	}

	private String reservationStatus(UUID reservationId) {
		return jdbcTemplate.queryForObject(
				"SELECT status FROM reservations WHERE id = ?",
				String.class,
				reservationId);
	}

	private List<UUID> ticketOwnerIds() {
		return jdbcTemplate.queryForList("""
				SELECT purchase_order.user_id
				FROM tickets ticket
				JOIN orders purchase_order ON purchase_order.id = ticket.order_id
				ORDER BY ticket.id
				""", UUID.class);
	}

	private int countRows(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private Set<String> redisKeys(String pattern) {
		Set<String> keys = redisTemplate.keys(pattern);
		return keys == null ? Set.of() : new TreeSet<>(keys);
	}

	private void cleanDatabaseAndRedis() {
		redisTemplate.execute((RedisCallback<Void>) connection -> {
			connection.serverCommands().flushDb();
			return null;
		});
		jdbcTemplate.update("DELETE FROM order_paid_analytics");
		jdbcTemplate.update("DELETE FROM processed_events");
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

	private static UUID uuid(JsonNode body, String field) {
		return UUID.fromString(body.get(field).asText());
	}

	private static List<UUID> eventSeatIds(ApiAttempt attempt) {
		return StreamSupport.stream(attempt.body().get("eventSeatIds").spliterator(), false)
				.map(JsonNode::asText)
				.map(UUID::fromString)
				.toList();
	}

	private static String holdRequest(List<UUID> eventSeatIds) {
		String ids = eventSeatIds.stream()
				.map(eventSeatId -> "\"%s\"".formatted(eventSeatId))
				.reduce((first, second) -> first + "," + second)
				.orElse("");
		return "{\"eventSeatIds\":[%s]}".formatted(ids);
	}

	@FunctionalInterface
	private interface ConcurrentAction<T> {

		T run(int index) throws Exception;
	}

	private record PublishedInventory(EventRecord event, List<EventSeatRecord> eventSeats) {
	}

	private record Checkout(UUID holdId, UUID reservationId, UUID orderId) {
	}

	private record PurchaseCommand(Checkout checkout, UserRecord user, String idempotencyKey) {
	}

	private record ApiAttempt(int status, JsonNode body) {

		private boolean created() {
			return status == 201;
		}
	}
}
