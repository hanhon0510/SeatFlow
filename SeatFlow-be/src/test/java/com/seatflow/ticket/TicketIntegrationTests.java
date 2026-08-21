package com.seatflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import com.seatflow.support.PostgresTestContainerSupport;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.venue.VenueMapper;
import com.seatflow.venue.VenueRecord;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TicketIntegrationTests extends PostgresTestContainerSupport {

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
	private TicketService ticketService;

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

	@BeforeEach
	void setUp() {
		cleanDatabase();
		when(seatHoldStore.isHoldActive(any(SeatHoldRecord.class))).thenReturn(true);
	}

	@AfterEach
	void tearDown() {
		cleanDatabase();
	}

	@Test
	void singleTicketCanBeListedAndOpenedWithEventSeatAndQrData() throws Exception {
		PaidFixture fixture = createPaidFixture(
				"ticket-single@example.com",
				List.of(new BigDecimal("125000.00")));
		UUID eventSeatId = fixture.inventory().eventSeats().getFirst().id();

		MvcResult listResult = mockMvc.perform(get("/api/v1/users/me/tickets")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.user())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].orderId").value(fixture.order().id().toString()))
				.andExpect(jsonPath("$[0].eventSeatId").value(eventSeatId.toString()))
				.andExpect(jsonPath("$[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$[0].event.id").value(fixture.inventory().event().id().toString()))
				.andExpect(jsonPath("$[0].event.name").value(fixture.inventory().event().name()))
				.andExpect(jsonPath("$[0].event.venueName").value(fixture.inventory().venue().name()))
				.andExpect(jsonPath("$[0].seat.sectionName").value("Orchestra"))
				.andExpect(jsonPath("$[0].seat.seatLabel").value("A1"))
				.andExpect(jsonPath("$[0].seat.price").value(125000.00))
				.andReturn();
		JsonNode ticket = responseBody(listResult).get(0);
		UUID ticketId = UUID.fromString(ticket.get("id").asText());
		String ticketCode = ticket.get("ticketCode").asText();

		assertThat(ticket.get("qrData").asText())
				.isEqualTo("seatflow:ticket:%s:%s".formatted(ticketId, ticketCode))
				.doesNotContain(fixture.user().email())
				.doesNotContain(fixture.inventory().event().name());

		mockMvc.perform(get("/api/v1/tickets/{ticketId}", ticketId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.user())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(ticketId.toString()))
				.andExpect(jsonPath("$.ticketCode").value(ticketCode))
				.andExpect(jsonPath("$.qrData").value("seatflow:ticket:%s:%s".formatted(ticketId, ticketCode)));
	}

	@Test
	void multipleTicketsAreIssuedForMultiplePurchasedSeats() throws Exception {
		PaidFixture fixture = createPaidFixture(
				"ticket-multiple@example.com",
				List.of(
						new BigDecimal("125000.00"),
						new BigDecimal("85000.00"),
						new BigDecimal("75000.00")));

		JsonNode response = responseBody(mockMvc.perform(get("/api/v1/users/me/tickets")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.user())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].seat.seatLabel").value("A1"))
				.andExpect(jsonPath("$[1].seat.seatLabel").value("A2"))
				.andExpect(jsonPath("$[2].seat.seatLabel").value("A3"))
				.andReturn());

		assertThat(countTickets(fixture.order().id())).isEqualTo(3);
		assertThat(eventSeatIds(response)).containsExactlyElementsOf(
				fixture.inventory().eventSeats().stream().map(EventSeatRecord::id).toList());
	}

	@Test
	void duplicateIssuanceCreatesNoDuplicates() throws Exception {
		PaidFixture fixture = createPaidFixture(
				"ticket-duplicate@example.com",
				List.of(new BigDecimal("125000.00"), new BigDecimal("85000.00")));
		List<String> firstCodes = ticketCodes(fixture.order().id());

		ticketService.issueTickets(
				fixture.order().id(),
				fixture.inventory().eventSeats().stream().map(EventSeatRecord::id).toList(),
				Instant.now());

		assertThat(countTickets(fixture.order().id())).isEqualTo(2);
		assertThat(ticketCodes(fixture.order().id())).containsExactlyElementsOf(firstCodes);
	}

	@Test
	void foreignAccessIsBlocked() throws Exception {
		PaidFixture fixture = createPaidFixture(
				"ticket-owner@example.com",
				List.of(new BigDecimal("125000.00")));
		UserRecord otherUser = insertUser("ticket-other@example.com");
		UUID ticketId = ticketIds(fixture.order().id()).getFirst();

		mockMvc.perform(get("/api/v1/tickets/{ticketId}", ticketId)
						.header(HttpHeaders.AUTHORIZATION, bearerToken(otherUser)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Ticket not found"));

		mockMvc.perform(get("/api/v1/users/me/tickets")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(otherUser)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void ticketCodesAreUniqueAndUrlSafe() throws Exception {
		PaidFixture fixture = createPaidFixture(
				"ticket-code@example.com",
				List.of(
						new BigDecimal("125000.00"),
						new BigDecimal("85000.00"),
						new BigDecimal("75000.00"),
						new BigDecimal("65000.00")));
		List<String> codes = ticketCodes(fixture.order().id());

		assertThat(codes).hasSize(4).doesNotHaveDuplicates();
		assertThat(codes).allSatisfy(code -> assertThat(code).matches("[A-Za-z0-9_-]{43}"));
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(DISTINCT ticket_code) FROM tickets WHERE order_id = ?",
				Integer.class,
				fixture.order().id())).isEqualTo(4);
	}

	private PaidFixture createPaidFixture(String email, List<BigDecimal> prices) throws Exception {
		EventInventory inventory = insertEventInventory(prices.size());
		UserRecord user = insertUser(email);
		ReservationRecord reservation = insertReservation(inventory, user, prices);
		OrderResponse order = orderService.createOrder(user.id(), new OrderCreateRequest(reservation.id()));

		mockMvc.perform(post("/api/v1/orders/{orderId}/payments", order.id())
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user))
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"token\":\"tok_success\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"));
		return new PaidFixture(inventory, user, reservation, order);
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
				"Ticket Hall %s".formatted(UUID.randomUUID()),
				"1 Ticket Street",
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
				"Ticket Event %s".formatted(UUID.randomUUID()),
				"Ticket integration test",
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
		return new EventInventory(
				venueMapper.findById(venue.id()),
				eventMapper.findById(event.id()),
				eventSeatMapper.findByEventId(event.id()));
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

	private List<UUID> ticketIds(UUID orderId) {
		return jdbcTemplate.query(
				"SELECT id FROM tickets WHERE order_id = ? ORDER BY id",
				(rs, rowNumber) -> rs.getObject("id", UUID.class),
				orderId);
	}

	private List<String> ticketCodes(UUID orderId) {
		return jdbcTemplate.queryForList(
				"SELECT ticket_code FROM tickets WHERE order_id = ? ORDER BY ticket_code",
				String.class,
				orderId);
	}

	private int countTickets(UUID orderId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM tickets WHERE order_id = ?",
				Integer.class,
				orderId);
	}

	private static List<UUID> eventSeatIds(JsonNode response) {
		return java.util.stream.StreamSupport.stream(response.spliterator(), false)
				.map(ticket -> UUID.fromString(ticket.get("eventSeatId").asText()))
				.toList();
	}

	private void cleanDatabase() {
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

	private record EventInventory(VenueRecord venue, EventRecord event, List<EventSeatRecord> eventSeats) {
	}

	private record PaidFixture(
			EventInventory inventory,
			UserRecord user,
			ReservationRecord reservation,
			OrderResponse order) {
	}
}
