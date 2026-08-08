package com.seatflow.hold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.event.EventMapper;
import com.seatflow.event.EventRecord;
import com.seatflow.event.EventSeatMapper;
import com.seatflow.event.EventSeatRecord;
import com.seatflow.event.EventSectionMapper;
import com.seatflow.event.EventSectionRecord;
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
@TestPropertySource(properties = "seatflow.holds.ttl=400ms")
class SeatHoldIntegrationTests extends RedisTestContainerSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
	void cleanDatabaseAndRedis() {
		redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
			connection.serverCommands().flushDb();
			return null;
		});
		jdbcTemplate.update("DELETE FROM refresh_tokens");
		jdbcTemplate.update("DELETE FROM event_seats");
		jdbcTemplate.update("DELETE FROM event_sections");
		jdbcTemplate.update("DELETE FROM events");
		jdbcTemplate.update("DELETE FROM seats");
		jdbcTemplate.update("DELETE FROM venue_sections");
		jdbcTemplate.update("DELETE FROM venues");
		jdbcTemplate.update("DELETE FROM users");
	}

	@Test
	void firstUserObtainsHoldAndSecondUserReceivesConflict() throws Exception {
		PublishedSeat publishedSeat = insertPublishedSeat(openSalesWindow(), true);
		UserRecord firstUser = insertUser("first@example.com");
		UserRecord secondUser = insertUser("second@example.com");

		mockMvc.perform(post("/api/v1/events/{eventId}/holds", publishedSeat.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(firstUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(publishedSeat.eventSeat().id())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.eventId").value(publishedSeat.event().id().toString()))
				.andExpect(jsonPath("$.eventSeatId").value(publishedSeat.eventSeat().id().toString()))
				.andExpect(jsonPath("$.userId").value(firstUser.id().toString()));

		String seatKey = SeatHoldRedisKeys.seat(publishedSeat.event().id(), publishedSeat.eventSeat().id());
		assertThat(redisTemplate.opsForValue().get(seatKey)).isNotBlank();
		assertThat(redisTemplate.getExpire(seatKey, TimeUnit.MILLISECONDS)).isPositive();

		mockMvc.perform(post("/api/v1/events/{eventId}/holds", publishedSeat.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(secondUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(publishedSeat.eventSeat().id())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Seat hold conflict"));
	}

	@Test
	void multiSeatRequestCreatesOneHoldForAllSeats() throws Exception {
		PublishedSeats publishedSeats = insertPublishedSeats(openSalesWindow(), true, 2);
		UserRecord user = insertUser("holder@example.com");
		EventSeatRecord firstSeat = publishedSeats.eventSeats().get(0);
		EventSeatRecord secondSeat = publishedSeats.eventSeats().get(1);

		mockMvc.perform(post("/api/v1/events/{eventId}/holds", publishedSeats.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(multiSeatRequestBody(firstSeat.id(), secondSeat.id())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.eventId").value(publishedSeats.event().id().toString()))
				.andExpect(jsonPath("$.eventSeatId").value(firstSeat.id().toString()))
				.andExpect(jsonPath("$.eventSeatIds[0]").value(firstSeat.id().toString()))
				.andExpect(jsonPath("$.eventSeatIds[1]").value(secondSeat.id().toString()))
				.andExpect(jsonPath("$.userId").value(user.id().toString()));

		String firstSeatHold = redisTemplate.opsForValue()
				.get(SeatHoldRedisKeys.seat(publishedSeats.event().id(), firstSeat.id()));
		String secondSeatHold = redisTemplate.opsForValue()
				.get(SeatHoldRedisKeys.seat(publishedSeats.event().id(), secondSeat.id()));
		assertThat(firstSeatHold).isNotBlank();
		assertThat(secondSeatHold).isEqualTo(firstSeatHold);
	}

	@Test
	void holdExpiresAndSeatCanBeHeldAgain() throws Exception {
		PublishedSeat publishedSeat = insertPublishedSeat(openSalesWindow(), true);
		UserRecord firstUser = insertUser("first@example.com");
		UserRecord secondUser = insertUser("second@example.com");

		mockMvc.perform(post("/api/v1/events/{eventId}/holds", publishedSeat.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(firstUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(publishedSeat.eventSeat().id())))
				.andExpect(status().isCreated());

		String seatKey = SeatHoldRedisKeys.seat(publishedSeat.event().id(), publishedSeat.eventSeat().id());
		for (int attempt = 0; attempt < 20 && redisTemplate.opsForValue().get(seatKey) != null; attempt++) {
			Thread.sleep(100);
		}
		assertThat(redisTemplate.opsForValue().get(seatKey)).isNull();

		mockMvc.perform(post("/api/v1/events/{eventId}/holds", publishedSeat.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(secondUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(publishedSeat.eventSeat().id())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value(secondUser.id().toString()));
	}

	@Test
	void seatFromAnotherEventIsRejected() throws Exception {
		PublishedSeat firstEventSeat = insertPublishedSeat(openSalesWindow(), true);
		PublishedSeat secondEventSeat = insertPublishedSeat(openSalesWindow(), true);
		UserRecord user = insertUser("holder@example.com");

		mockMvc.perform(post("/api/v1/events/{eventId}/holds", secondEventSeat.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(firstEventSeat.eventSeat().id())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Seat hold conflict"));
	}

	@Test
	void soldOrBlockedSeatCannotBeHeld() throws Exception {
		PublishedSeat soldSeat = insertPublishedSeat(openSalesWindow(), true);
		PublishedSeat blockedSeat = insertPublishedSeat(openSalesWindow(), false);
		UserRecord user = insertUser("holder@example.com");
		jdbcTemplate.update(
				"UPDATE event_seats SET permanent_status = 'SOLD' WHERE id = ?",
				soldSeat.eventSeat().id());

		mockMvc.perform(post("/api/v1/events/{eventId}/holds", soldSeat.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(soldSeat.eventSeat().id())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Seat hold conflict"));

		mockMvc.perform(post("/api/v1/events/{eventId}/holds", blockedSeat.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(blockedSeat.eventSeat().id())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Seat hold conflict"));
	}

	@Test
	void closedSalesCannotBeHeld() throws Exception {
		PublishedSeat publishedSeat = insertPublishedSeat(closedSalesWindow(), true);
		UserRecord user = insertUser("holder@example.com");

		mockMvc.perform(post("/api/v1/events/{eventId}/holds", publishedSeat.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(publishedSeat.eventSeat().id())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Seat hold conflict"));
	}

	private PublishedSeat insertPublishedSeat(SalesWindow salesWindow, boolean salesEnabled) {
		PublishedSeats publishedSeats = insertPublishedSeats(salesWindow, salesEnabled, 1);
		return new PublishedSeat(publishedSeats.event(), publishedSeats.eventSeats().getFirst());
	}

	private PublishedSeats insertPublishedSeats(SalesWindow salesWindow, boolean salesEnabled, int seatCount) {
		VenueRecord venue = insertVenue();
		VenueSectionRecord section = insertSection(venue.id());
		for (int seatNumber = 1; seatNumber <= seatCount; seatNumber++) {
			insertSeat(section.id(), seatNumber);
		}
		EventRecord event = insertEvent(venue.id(), salesWindow);
		insertEventSection(event.id(), section.id(), salesEnabled);
		assertThat(eventSeatMapper.insertForDraftEvent(event.id())).isEqualTo(seatCount);
		assertThat(eventMapper.publishDraft(event.id())).isEqualTo(1);
		return new PublishedSeats(eventMapper.findById(event.id()), eventSeatMapper.findByEventId(event.id()));
	}

	private UserRecord insertUser(String email) {
		UserRecord user = UserRecord.forInsert(UUID.randomUUID(), email, "{bcrypt}hash", UserRole.USER);
		userMapper.insertWithRole(user);
		return user;
	}

	private VenueRecord insertVenue() {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(),
				"Hold Hall %s".formatted(UUID.randomUUID()),
				"1 Hold Street",
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

	private SeatRecord insertSeat(UUID sectionId) {
		return insertSeat(sectionId, 1);
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

	private EventRecord insertEvent(UUID venueId, SalesWindow salesWindow) {
		EventRecord event = EventRecord.forInsert(
				UUID.randomUUID(),
				venueId,
				"Holdable Event %s".formatted(UUID.randomUUID()),
				"One seat hold test",
				salesWindow.eventStart(),
				salesWindow.salesStart(),
				salesWindow.salesEnd());
		eventMapper.insert(event);
		return eventMapper.findById(event.id());
	}

	private void insertEventSection(UUID eventId, UUID sectionId, boolean salesEnabled) {
		EventSectionRecord section = EventSectionRecord.forInsert(
				UUID.randomUUID(),
				eventId,
				sectionId,
				new BigDecimal("125000.00"),
				salesEnabled);
		assertThat(eventSectionMapper.insertForDraftEvent(section)).isEqualTo(1);
	}

	private String bearerToken(UserRecord user) {
		return "Bearer " + jwtTokenService.issueAccessToken(user).accessToken();
	}

	private static String requestBody(UUID eventSeatId) {
		return "{\"eventSeatId\":\"%s\"}".formatted(eventSeatId);
	}

	private static String multiSeatRequestBody(UUID firstEventSeatId, UUID secondEventSeatId) {
		return "{\"eventSeatIds\":[\"%s\",\"%s\"]}".formatted(firstEventSeatId, secondEventSeatId);
	}

	private static SalesWindow openSalesWindow() {
		Instant now = Instant.now();
		return new SalesWindow(now.plus(Duration.ofDays(1)), now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(2)));
	}

	private static SalesWindow closedSalesWindow() {
		Instant now = Instant.now();
		return new SalesWindow(now.plus(Duration.ofDays(1)), now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(1)));
	}

	private record SalesWindow(Instant eventStart, Instant salesStart, Instant salesEnd) {
	}

	private record PublishedSeat(EventRecord event, EventSeatRecord eventSeat) {
	}

	private record PublishedSeats(EventRecord event, java.util.List<EventSeatRecord> eventSeats) {
	}
}
