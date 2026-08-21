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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import com.seatflow.order.OrderStatus;
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
class CheckoutInfrastructureIntegrationTests extends RedisTestContainerSupport {

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
		cleanDatabaseAndRedis();
	}

	@AfterEach
	void tearDown() {
		cleanDatabaseAndRedis();
	}

	@Test
	void checkoutFlowUsesPostgreSqlAndRedisWithoutMockedInfrastructure() throws Exception {
		PublishedInventory inventory = insertPublishedInventory(2);
		UserRecord user = insertUser("checkout-real-infra@example.com");
		List<UUID> eventSeatIds = inventory.eventSeats().stream().map(EventSeatRecord::id).toList();
		String idempotencyKey = "checkout-real-infra-" + UUID.randomUUID();

		JsonNode hold = responseBody(mockMvc.perform(post("/api/v1/events/{eventId}/holds", inventory.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(holdRequest(eventSeatIds)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.eventSeatIds.length()").value(2))
				.andReturn());
		UUID holdId = UUID.fromString(hold.get("holdId").asText());
		assertRedisHoldExists(inventory.event().id(), eventSeatIds, holdId, user.id());

		JsonNode reservation = responseBody(mockMvc.perform(post("/api/v1/reservations")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reservationRequest(holdId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andReturn());
		UUID reservationId = UUID.fromString(reservation.get("id").asText());
		assertThat(reservation.get("totalAmount").decimalValue()).isEqualByComparingTo("250000.00");

		JsonNode order = responseBody(mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderRequest(reservationId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.totalAmount").value(250000.00))
				.andReturn());
		UUID orderId = UUID.fromString(order.get("id").asText());

		JsonNode firstPayment = responseBody(createPayment(orderId, user, idempotencyKey)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andReturn());
		JsonNode retryPayment = responseBody(createPayment(orderId, user, idempotencyKey)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andReturn());

		assertThat(retryPayment).isEqualTo(firstPayment);
		assertThat(countRows("payments")).isEqualTo(1);
		assertThat(countRows("idempotency_records")).isEqualTo(1);
		assertThat(countRows("tickets")).isEqualTo(2);
		assertThat(countRows("outbox_events")).isEqualTo(1);
		assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID.name());
		assertThat(reservationStatus(reservationId)).isEqualTo(ReservationStatus.CONFIRMED.name());
		assertThat(eventSeatMapper.findByEventId(inventory.event().id()))
				.extracting(EventSeatRecord::permanentStatus)
				.containsOnly(EventSeatStatus.SOLD);
		assertRedisHoldWasReleased(inventory.event().id(), eventSeatIds, holdId, user.id());
	}

	private org.springframework.test.web.servlet.ResultActions createPayment(
			UUID orderId,
			UserRecord user,
			String idempotencyKey) throws Exception {
		return mockMvc.perform(post("/api/v1/orders/{orderId}/payments", orderId)
				.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"tok_success\"}"));
	}

	private PublishedInventory insertPublishedInventory(int seatCount) {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(),
				"Checkout Hall %s".formatted(UUID.randomUUID()),
				"1 Checkout Street",
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
				"Checkout Event %s".formatted(UUID.randomUUID()),
				"Checkout infrastructure integration test",
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
		assertThat(eventMapper.publishDraft(event.id())).isEqualTo(1);
		return new PublishedInventory(eventMapper.findById(event.id()), eventSeatMapper.findByEventId(event.id()));
	}

	private UserRecord insertUser(String email) {
		UserRecord user = UserRecord.forInsert(UUID.randomUUID(), email, "{bcrypt}hash", UserRole.USER);
		userMapper.insertWithRole(user);
		return userMapper.findById(user.id());
	}

	private void assertRedisHoldExists(UUID eventId, List<UUID> eventSeatIds, UUID holdId, UUID userId) {
		for (UUID eventSeatId : eventSeatIds) {
			assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(eventId, eventSeatId)))
					.isEqualTo(holdId.toString());
		}
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.data(holdId))).isNotBlank();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.user(userId))).isEqualTo(holdId.toString());
	}

	private void assertRedisHoldWasReleased(UUID eventId, List<UUID> eventSeatIds, UUID holdId, UUID userId) {
		for (UUID eventSeatId : eventSeatIds) {
			assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.seat(eventId, eventSeatId))).isNull();
		}
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.data(holdId))).isNull();
		assertThat(redisTemplate.opsForValue().get(SeatHoldRedisKeys.user(userId))).isNull();
	}

	private JsonNode responseBody(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private String bearerToken(UserRecord user) {
		return "Bearer " + jwtTokenService.issueAccessToken(user).accessToken();
	}

	private int countRows(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private String orderStatus(UUID orderId) {
		return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
	}

	private String reservationStatus(UUID reservationId) {
		return jdbcTemplate.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class, reservationId);
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

	private static String holdRequest(List<UUID> eventSeatIds) {
		return """
				{"eventSeatIds":["%s","%s"]}
				""".formatted(eventSeatIds.get(0), eventSeatIds.get(1));
	}

	private static String reservationRequest(UUID holdId) {
		return "{\"holdId\":\"%s\"}".formatted(holdId);
	}

	private static String orderRequest(UUID reservationId) {
		return "{\"reservationId\":\"%s\"}".formatted(reservationId);
	}

	private record PublishedInventory(EventRecord event, List<EventSeatRecord> eventSeats) {
	}
}
