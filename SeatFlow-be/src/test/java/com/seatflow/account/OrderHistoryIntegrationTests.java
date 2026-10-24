package com.seatflow.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.cancellation.EventCancellationRequest;
import com.seatflow.cancellation.EventCancellationService;
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
import com.seatflow.promo.PromoCodeCreateRequest;
import com.seatflow.promo.PromoCodeService;
import com.seatflow.promo.PromoDiscountType;
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
class OrderHistoryIntegrationTests extends PostgresTestContainerSupport {

	private static final String URL = "/api/v1/users/me/order-history";

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
	private EventCancellationService cancellationService;

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
	void aPaidOrderShowsTheEventTheSeatsAndTheTicketsPaidFor() throws Exception {
		UserRecord buyer = insertUser("buyer@example.com", UserRole.USER);
		Placed placed = place(buyer, null, "125000", "75000");
		pay(placed, buyer);

		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(1))
				.andExpect(jsonPath("$.items[0].orderId").value(placed.order().id().toString()))
				.andExpect(jsonPath("$.items[0].status").value("PAID"))
				.andExpect(jsonPath("$.items[0].totalAmount").value(200000))
				.andExpect(jsonPath("$.items[0].currency").value("VND"))
				.andExpect(jsonPath("$.items[0].event.id").value(placed.event().id().toString()))
				.andExpect(jsonPath("$.items[0].event.name").value(placed.event().name()))
				.andExpect(jsonPath("$.items[0].event.venueName").value("History Hall"))
				.andExpect(jsonPath("$.items[0].event.venueTimezone").value("Asia/Ho_Chi_Minh"))
				.andExpect(jsonPath("$.items[0].event.cancelled").value(false))
				.andExpect(jsonPath("$.items[0].seats.length()").value(2))
				.andExpect(jsonPath("$.items[0].seats[0].sectionName").value("Orchestra"))
				.andExpect(jsonPath("$.items[0].seats[0].seatLabel").value("A1"))
				.andExpect(jsonPath("$.items[0].seats[0].ticketStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.items[0].seats[0].ticketId").isNotEmpty())
				.andExpect(jsonPath("$.items[0].seats[1].seatLabel").value("A2"))
				.andExpect(jsonPath("$.items[0].refund").doesNotExist());
	}

	@Test
	void anUnpaidOrderListsItsSeatsWithoutTickets() throws Exception {
		UserRecord buyer = insertUser("pending@example.com", UserRole.USER);
		place(buyer, null, "100000");

		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].status").value("PENDING"))
				.andExpect(jsonPath("$.items[0].seats.length()").value(1))
				.andExpect(jsonPath("$.items[0].seats[0].ticketId").doesNotExist())
				.andExpect(jsonPath("$.items[0].seats[0].ticketStatus").doesNotExist());
	}

	@Test
	void aRefundedOrderShowsWhatWasReturnedAndWhy() throws Exception {
		UserRecord buyer = insertUser("refunded@example.com", UserRole.USER);
		Placed placed = place(buyer, null, "100000");
		pay(placed, buyer);
		refundService.refundOrder(placed.order().id(), admin.id(), new RefundRequest("Duplicate purchase"));

		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].status").value("REFUNDED"))
				.andExpect(jsonPath("$.items[0].refund.amount").value(100000))
				.andExpect(jsonPath("$.items[0].refund.reason").value("Duplicate purchase"))
				.andExpect(jsonPath("$.items[0].refund.refundedAt").isNotEmpty())
				.andExpect(jsonPath("$.items[0].seats[0].ticketStatus").value("CANCELLED"));
	}

	@Test
	void anOrderForACancelledEventExplainsWhy() throws Exception {
		UserRecord buyer = insertUser("cancelled@example.com", UserRole.USER);
		Placed placed = place(buyer, null, "100000");
		pay(placed, buyer);
		cancellationService.cancelEvent(placed.event().id(), admin.id(), new EventCancellationRequest("Venue flooded"));

		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].status").value("REFUNDED"))
				.andExpect(jsonPath("$.items[0].event.cancelled").value(true))
				.andExpect(jsonPath("$.items[0].event.cancellationReason").value("Venue flooded"))
				.andExpect(jsonPath("$.items[0].refund.reason").value("Event cancelled: Venue flooded"));
	}

	@Test
	void aPromoDiscountIsShown() throws Exception {
		promoCodeService.create(new PromoCodeCreateRequest(
				"SPRING", PromoDiscountType.FIXED, new BigDecimal("20000"), null, null, 5));
		UserRecord buyer = insertUser("promo@example.com", UserRole.USER);
		Placed placed = place(buyer, "SPRING", "100000");
		pay(placed, buyer);

		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].promoCode").value("SPRING"))
				.andExpect(jsonPath("$.items[0].discountAmount").value(20000))
				.andExpect(jsonPath("$.items[0].totalAmount").value(80000));
	}

	@Test
	void buyersOnlyEverSeeTheirOwnOrdersNewestFirst() throws Exception {
		UserRecord buyer = insertUser("mine@example.com", UserRole.USER);
		UserRecord other = insertUser("theirs@example.com", UserRole.USER);
		Placed older = place(buyer, null, "100000");
		Placed newer = place(buyer, null, "110000");
		place(other, null, "120000");
		jdbcTemplate.update(
				"UPDATE orders SET created_at = NOW() - INTERVAL '2 days', updated_at = NOW() - INTERVAL '2 days' "
						+ "WHERE id = ?",
				older.order().id());

		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(2))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].orderId").value(newer.order().id().toString()))
				.andExpect(jsonPath("$.items[1].orderId").value(older.order().id().toString()))
				.andExpect(jsonPath("$.items[0].totalAmount").value(110000))
				.andExpect(jsonPath("$.items[1].totalAmount").value(100000));
	}

	@Test
	void historyIsPaged() throws Exception {
		UserRecord buyer = insertUser("paged@example.com", UserRole.USER);
		place(buyer, null, "100000");
		place(buyer, null, "100000");
		place(buyer, null, "100000");

		mockMvc.perform(get(URL).param("page", "1").param("size", "2")
						.header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalItems").value(3))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	void aBuyerWithNoOrdersGetsAnEmptyPage() throws Exception {
		UserRecord buyer = insertUser("none@example.com", UserRole.USER);

		mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(0))
				.andExpect(jsonPath("$.totalPages").value(0))
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void badPagingAndMissingLoginAreRefused() throws Exception {
		UserRecord buyer = insertUser("bad@example.com", UserRole.USER);

		mockMvc.perform(get(URL).param("size", "0").header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get(URL).param("size", "51").header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get(URL).param("page", "-1").header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
	}

	private void pay(Placed placed, UserRecord buyer) {
		paymentService.createPayment(placed.order().id(), buyer.id(), new PaymentCreateRequest("tok_success"));
	}

	/** An unpaid order of the given prices, each on its own seat of a fresh event. */
	private Placed place(UserRecord buyer, String promoCode, String... prices) {
		EventRecord event = insertEventWithSeats(prices.length);
		List<EventSeatRecord> seats = eventSeatMapper.findByEventId(event.id());
		Instant now = Instant.now();
		ReservationRecord reservation = new ReservationRecord(
				UUID.randomUUID(), buyer.id(), event.id(), UUID.randomUUID(), ReservationStatus.PENDING_PAYMENT,
				now.plusSeconds(60), new BigDecimal("1.00"), now.minusSeconds(60), now.minusSeconds(60));
		reservationMapper.insert(reservation);
		reservationItemMapper.batchInsert(java.util.stream.IntStream.range(0, prices.length)
				.mapToObj(index -> new ReservationItemRecord(
						UUID.randomUUID(), reservation.id(), seats.get(index).id(),
						new BigDecimal(prices[index]), now.minusSeconds(60)))
				.toList());
		OrderResponse order = orderService.createOrder(buyer.id(), new OrderCreateRequest(reservation.id(), promoCode));
		return new Placed(event, order);
	}

	private EventRecord insertEventWithSeats(int seatCount) {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(), "History Hall", "1 History Street", "Ho Chi Minh City", "Vietnam",
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
				UUID.randomUUID(), venue.id(), "History Event %s".formatted(UUID.randomUUID()),
				"Order history integration test", now.plus(Duration.ofDays(3)), now.minus(Duration.ofHours(1)),
				now.plus(Duration.ofHours(2)));
		eventMapper.insert(event);
		eventSectionMapper.insertForDraftEvent(EventSectionRecord.forInsert(
				UUID.randomUUID(), event.id(), section.id(), new BigDecimal("125000.00"), true));
		eventSeatMapper.insertForDraftEvent(event.id());
		return eventMapper.findById(event.id());
	}

	private UserRecord insertUser(String email, UserRole role) {
		UserRecord user = UserRecord.forInsert(UUID.randomUUID(), email, "{bcrypt}hash", role);
		userMapper.insertWithRole(user);
		return userMapper.findById(user.id());
	}

	private String bearer(UserRecord user) {
		return "Bearer " + jwtTokenService.issueAccessToken(user).accessToken();
	}

	private void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM order_reminders");
		jdbcTemplate.update("DELETE FROM event_reminder_settings");
		jdbcTemplate.update("DELETE FROM processed_events");
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

	private record Placed(EventRecord event, OrderResponse order) {
	}
}
