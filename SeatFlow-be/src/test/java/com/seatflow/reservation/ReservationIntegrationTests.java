package com.seatflow.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.seatflow.event.EventSectionMapper;
import com.seatflow.event.EventSectionRecord;
import com.seatflow.hold.SeatHoldRecord;
import com.seatflow.hold.SeatHoldStore;
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
class ReservationIntegrationTests extends RedisTestContainerSupport {

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
	private ReservationService reservationService;

	@Autowired
	private ReservationMapper reservationMapper;

	@Autowired
	private ReservationItemMapper reservationItemMapper;

	@Autowired
	private SeatHoldStore seatHoldStore;

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
	void validHoldCreatesReservationAndRepeatedRequestReturnsSameReservation() throws Exception {
		PublishedSeats publishedSeats = insertPublishedSeats(2);
		UserRecord user = insertUser("buyer@example.com");
		SeatHoldRecord hold = createHold(publishedSeats, user, Instant.now().plusSeconds(30));

		MvcResult firstResult = createReservation(hold.holdId(), user);
		JsonNode firstResponse = responseBody(firstResult);
		UUID reservationId = UUID.fromString(firstResponse.get("id").asText());

		assertThat(firstResponse.get("status").asText()).isEqualTo("PENDING_PAYMENT");
		assertThat(firstResponse.get("expiresAt").asText()).isEqualTo(hold.expiresAt().toString());
		assertThat(firstResponse.get("totalAmount").decimalValue()).isEqualByComparingTo("250000.00");
		assertThat(firstResponse.get("items")).hasSize(2);
		assertThat(firstResponse.get("items").get(0).get("price").decimalValue())
				.isEqualByComparingTo("125000.00");
		assertThat(firstResponse.get("items").get(1).get("price").decimalValue())
				.isEqualByComparingTo("125000.00");

		mockMvc.perform(post("/api/v1/reservations")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reservationRequest(hold.holdId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(reservationId.toString()))
				.andExpect(jsonPath("$.items.length()").value(2));

		mockMvc.perform(get("/api/v1/reservations/{reservationId}", reservationId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(reservationId.toString()))
				.andExpect(jsonPath("$.holdId").value(hold.holdId().toString()))
				.andExpect(jsonPath("$.totalAmount").value(250000.00))
				.andExpect(jsonPath("$.items.length()").value(2));

		assertThat(countRows("reservations")).isEqualTo(1);
		assertThat(countRows("reservation_items")).isEqualTo(2);
	}

	@Test
	void foreignHoldIsRejected() throws Exception {
		PublishedSeats publishedSeats = insertPublishedSeats(1);
		UserRecord owner = insertUser("owner@example.com");
		UserRecord otherUser = insertUser("other@example.com");
		SeatHoldRecord hold = createHold(publishedSeats, owner, Instant.now().plusSeconds(30));

		mockMvc.perform(post("/api/v1/reservations")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(otherUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reservationRequest(hold.holdId())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Forbidden"));

		assertThat(countRows("reservations")).isZero();
		assertThat(countRows("reservation_items")).isZero();
	}

	@Test
	void expiredHoldIsRejected() throws Exception {
		PublishedSeats publishedSeats = insertPublishedSeats(1);
		UserRecord user = insertUser("expired@example.com");
		SeatHoldRecord hold = createHold(publishedSeats, user, Instant.now().minusSeconds(1));

		mockMvc.perform(post("/api/v1/reservations")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reservationRequest(hold.holdId())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Seat hold not found"));

		assertThat(countRows("reservations")).isZero();
		assertThat(countRows("reservation_items")).isZero();
	}

	@Test
	void itemBatchFailureRollsBackReservationHeader() {
		PublishedSeats publishedSeats = insertPublishedSeats(2);
		UserRecord user = insertUser("rollback@example.com");
		SeatHoldRecord hold = createHold(publishedSeats, user, Instant.now().plusSeconds(30));
		installFailingReservationItemTrigger();

		try {
			assertThatThrownBy(() -> reservationService.createReservation(
					user.id(),
					new ReservationCreateRequest(hold.holdId())))
					.isInstanceOf(RuntimeException.class);

			assertThat(countRows("reservations")).isZero();
			assertThat(countRows("reservation_items")).isZero();
		}
		finally {
			removeFailingReservationItemTrigger();
		}
	}

	@Test
	void concurrentCreationReturnsOneDurableReservation() throws Exception {
		PublishedSeats publishedSeats = insertPublishedSeats(2);
		UserRecord user = insertUser("concurrent@example.com");
		SeatHoldRecord hold = createHold(publishedSeats, user, Instant.now().plusSeconds(30));
		ReservationCreateRequest request = new ReservationCreateRequest(hold.holdId());
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<ReservationResponse> first = executor.submit(() -> {
				ready.countDown();
				start.await();
				return reservationService.createReservation(user.id(), request);
			});
			Future<ReservationResponse> second = executor.submit(() -> {
				ready.countDown();
				start.await();
				return reservationService.createReservation(user.id(), request);
			});
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			ReservationResponse firstResponse = first.get(10, TimeUnit.SECONDS);
			ReservationResponse secondResponse = second.get(10, TimeUnit.SECONDS);

			assertThat(secondResponse.id()).isEqualTo(firstResponse.id());
			assertThat(firstResponse.items()).hasSize(2);
			assertThat(secondResponse.items()).hasSize(2);
			assertThat(countRows("reservations")).isEqualTo(1);
			assertThat(countRows("reservation_items")).isEqualTo(2);
		}
		finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void mapperFindsByHoldAndOwnerAndConditionallyUpdatesStatus() {
		PublishedSeats publishedSeats = insertPublishedSeats(1);
		UserRecord user = insertUser("mapper@example.com");
		SeatHoldRecord hold = createHold(publishedSeats, user, Instant.now().plusSeconds(30));
		ReservationResponse created = reservationService.createReservation(
				user.id(),
				new ReservationCreateRequest(hold.holdId()));

		assertThat(reservationMapper.findByHoldId(hold.holdId()).id()).isEqualTo(created.id());
		assertThat(reservationMapper.findByIdAndUser(created.id(), user.id())).isNotNull();
		assertThat(reservationMapper.findByIdAndUser(created.id(), UUID.randomUUID())).isNull();
		assertThat(reservationItemMapper.findByReservationId(created.id())).hasSize(1);

		Instant updatedAt = Instant.now();
		assertThat(reservationMapper.updateStatus(
				created.id(),
				ReservationStatus.PENDING_PAYMENT,
				ReservationStatus.CONFIRMED,
				updatedAt)).isEqualTo(1);
		assertThat(reservationMapper.updateStatus(
				created.id(),
				ReservationStatus.PENDING_PAYMENT,
				ReservationStatus.CANCELLED,
				updatedAt.plusSeconds(1))).isZero();
		assertThat(reservationMapper.findByIdAndUser(created.id(), user.id()).status())
				.isEqualTo(ReservationStatus.CONFIRMED);
	}

	private MvcResult createReservation(UUID holdId, UserRecord user) throws Exception {
		return mockMvc.perform(post("/api/v1/reservations")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reservationRequest(holdId)))
				.andExpect(status().isCreated())
				.andReturn();
	}

	private JsonNode responseBody(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private SeatHoldRecord createHold(PublishedSeats publishedSeats, UserRecord user, Instant expiresAt) {
		List<UUID> eventSeatIds = publishedSeats.eventSeats().stream().map(EventSeatRecord::id).toList();
		List<UUID> seatIds = publishedSeats.eventSeats().stream().map(EventSeatRecord::seatId).toList();
		SeatHoldRecord hold = new SeatHoldRecord(
				UUID.randomUUID(),
				publishedSeats.event().id(),
				eventSeatIds,
				seatIds,
				user.id(),
				expiresAt);
		assertThat(seatHoldStore.createHold(hold, Duration.ofSeconds(30))).isTrue();
		return hold;
	}

	private PublishedSeats insertPublishedSeats(int seatCount) {
		VenueRecord venue = insertVenue();
		VenueSectionRecord section = insertSection(venue.id());
		for (int seatNumber = 1; seatNumber <= seatCount; seatNumber++) {
			insertSeat(section.id(), seatNumber);
		}
		EventRecord event = insertEvent(venue.id());
		insertEventSection(event.id(), section.id());
		assertThat(eventSeatMapper.insertForDraftEvent(event.id())).isEqualTo(seatCount);
		assertThat(eventMapper.publishDraft(event.id())).isEqualTo(1);
		return new PublishedSeats(eventMapper.findById(event.id()), eventSeatMapper.findByEventId(event.id()));
	}

	private UserRecord insertUser(String email) {
		UserRecord user = UserRecord.forInsert(UUID.randomUUID(), email, "{bcrypt}hash", UserRole.USER);
		userMapper.insertWithRole(user);
		return userMapper.findById(user.id());
	}

	private VenueRecord insertVenue() {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(),
				"Reservation Hall %s".formatted(UUID.randomUUID()),
				"1 Reservation Street",
				"Ho Chi Minh City",
				"Vietnam",
				"Asia/Ho_Chi_Minh");
		venueMapper.insert(venue);
		return venueMapper.findById(venue.id());
	}

	private VenueSectionRecord insertSection(UUID venueId) {
		VenueSectionRecord section = VenueSectionRecord.forInsert(UUID.randomUUID(), venueId, "Orchestra", 1);
		sectionMapper.insert(section);
		return sectionMapper.findById(section.id());
	}

	private SeatRecord insertSeat(UUID sectionId, int seatNumber) {
		SeatRecord seat = SeatRecord.forInsert(
				UUID.randomUUID(),
				sectionId,
				"A",
				seatNumber,
				"A%s".formatted(seatNumber),
				false);
		seatMapper.insert(seat);
		return seatMapper.findById(seat.id());
	}

	private EventRecord insertEvent(UUID venueId) {
		Instant now = Instant.now();
		EventRecord event = EventRecord.forInsert(
				UUID.randomUUID(),
				venueId,
				"Reservable Event %s".formatted(UUID.randomUUID()),
				"Reservation integration test",
				now.plus(Duration.ofDays(1)),
				now.minus(Duration.ofHours(1)),
				now.plus(Duration.ofHours(2)));
		eventMapper.insert(event);
		return eventMapper.findById(event.id());
	}

	private void insertEventSection(UUID eventId, UUID sectionId) {
		EventSectionRecord section = EventSectionRecord.forInsert(
				UUID.randomUUID(),
				eventId,
				sectionId,
				new BigDecimal("125000.00"),
				true);
		assertThat(eventSectionMapper.insertForDraftEvent(section)).isEqualTo(1);
	}

	private String bearerToken(UserRecord user) {
		return "Bearer " + jwtTokenService.issueAccessToken(user).accessToken();
	}

	private int countRows(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private void installFailingReservationItemTrigger() {
		jdbcTemplate.execute("""
				CREATE OR REPLACE FUNCTION seatflow_test_fail_reservation_item()
				RETURNS trigger AS $$
				BEGIN
				    RAISE EXCEPTION 'forced reservation item failure';
				END;
				$$ LANGUAGE plpgsql
				""");
		jdbcTemplate.execute("""
				CREATE TRIGGER seatflow_test_fail_reservation_item_trigger
				BEFORE INSERT ON reservation_items
				FOR EACH ROW EXECUTE FUNCTION seatflow_test_fail_reservation_item()
				""");
	}

	private void removeFailingReservationItemTrigger() {
		jdbcTemplate.execute("DROP TRIGGER IF EXISTS seatflow_test_fail_reservation_item_trigger ON reservation_items");
		jdbcTemplate.execute("DROP FUNCTION IF EXISTS seatflow_test_fail_reservation_item()");
	}

	private void cleanDatabaseAndRedis() {
		redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
			connection.serverCommands().flushDb();
			return null;
		});
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

	private static String reservationRequest(UUID holdId) {
		return "{\"holdId\":\"%s\"}".formatted(holdId);
	}

	private record PublishedSeats(EventRecord event, List<EventSeatRecord> eventSeats) {
	}
}
