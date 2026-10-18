package com.seatflow.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.cancellation.EventCancellationRequest;
import com.seatflow.cancellation.EventCancellationService;
import com.seatflow.event.EventMapper;
import com.seatflow.event.EventRecord;
import com.seatflow.event.EventSeatMapper;
import com.seatflow.event.EventSectionMapper;
import com.seatflow.event.EventSectionRecord;
import com.seatflow.hold.SeatHoldRecord;
import com.seatflow.hold.SeatHoldStore;
import com.seatflow.notification.EmailDeliveryException;
import com.seatflow.notification.EmailGateway;
import com.seatflow.notification.EmailMessage;
import com.seatflow.notification.OrderEmailTemplates;
import com.seatflow.notification.OrderNotificationMapper;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.order.OrderCreateRequest;
import com.seatflow.order.OrderResponse;
import com.seatflow.order.OrderService;
import com.seatflow.payment.PaymentCreateRequest;
import com.seatflow.payment.PaymentService;
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
class EventReminderIntegrationTests extends PostgresTestContainerSupport {

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

	@Autowired
	private EventReminderMapper reminderMapper;

	@Autowired
	private OrderNotificationMapper notificationMapper;

	@Autowired
	private OrderEmailTemplates templates;

	@Autowired
	private BusinessMetrics businessMetrics;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@MockitoBean
	private SeatHoldStore seatHoldStore;

	// Built by hand with a mock gateway so the messages can be read, sharing the application context
	// of the other integration tests. The default of 24 hours before is the application default.
	private final EmailGateway gateway = mock(EmailGateway.class);

	private EventReminderService service;

	private UserRecord admin;

	@BeforeEach
	void setUp() {
		cleanDatabase();
		reset(gateway);
		when(seatHoldStore.isHoldActive(any(SeatHoldRecord.class))).thenReturn(true);
		admin = insertUser("admin@example.com", UserRole.ADMIN);
		service = new EventReminderService(
				eventMapper,
				reminderMapper,
				notificationMapper,
				templates,
				gateway,
				businessMetrics,
				new ReminderProperties(true, 24, 100, 60_000L),
				transactionTemplate,
				Clock.systemUTC());
	}

	@AfterEach
	void tearDown() {
		cleanDatabase();
	}

	@Test
	void remindsBuyersOnceWhenTheDefaultWindowOpens() {
		Booking booking = book(Duration.ofHours(20), "buyer@example.com");

		assertThat(service.sendDueReminders()).isEqualTo(1);

		ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
		verify(gateway).send(captor.capture());
		assertThat(captor.getValue().to()).isEqualTo("buyer@example.com");
		assertThat(captor.getValue().subject()).isEqualTo("Reminder: " + booking.event().name() + " starts soon");
		assertThat(captor.getValue().text()).contains("- Orchestra, row A, seat A1");

		assertThat(service.sendDueReminders()).isZero();
		verify(gateway, times(1)).send(any());
		assertThat(count("SELECT COUNT(*) FROM order_reminders WHERE order_id = ?", booking.order().id()))
				.isEqualTo(1);
	}

	@Test
	void doesNothingBeforeTheWindowOpens() {
		book(Duration.ofHours(30), "early@example.com");

		assertThat(service.sendDueReminders()).isZero();

		verify(gateway, times(0)).send(any());
	}

	@Test
	void anAdminCanMoveTheWindowEarlierOrTurnRemindersOff() {
		Booking booking = book(Duration.ofHours(30), "custom@example.com");

		saveSettings(booking.event(), true, 48);
		assertThat(service.sendDueReminders()).isEqualTo(1);

		Booking other = book(Duration.ofHours(10), "off@example.com");
		saveSettings(other.event(), false, 24);
		assertThat(service.sendDueReminders()).isZero();
	}

	@Test
	void aSettingShorterThanTheDefaultHoldsRemindersBack() {
		Booking booking = book(Duration.ofHours(20), "later@example.com");
		saveSettings(booking.event(), true, 6);

		assertThat(service.sendDueReminders()).isZero();
	}

	@Test
	void buyersWhoBookedAfterTheWindowOpenedAreNotRemindedStraightAway() {
		Booking booking = book(Duration.ofHours(20), "lastminute@example.com");
		jdbcTemplate.update("UPDATE orders SET updated_at = NOW() WHERE id = ?", booking.order().id());

		assertThat(service.sendDueReminders()).isZero();
	}

	@Test
	void refundedAndCancelledOrdersAreNeverReminded() {
		Booking refunded = book(Duration.ofHours(20), "refunded@example.com");
		refundService.refundOrder(refunded.order().id(), admin.id(), new RefundRequest("test"));
		Booking cancelledEvent = book(Duration.ofHours(20), "cancelled@example.com");
		cancellationService.cancelEvent(cancelledEvent.event().id(), admin.id(), EventCancellationRequest.NONE);

		assertThat(service.sendDueReminders()).isZero();
	}

	@Test
	void aFailedSendIsRetriedOnTheNextPass() {
		Booking booking = book(Duration.ofHours(20), "flaky@example.com");
		doThrow(new EmailDeliveryException("smtp down", new RuntimeException())).when(gateway).send(any());

		assertThat(service.sendDueReminders()).isZero();
		assertThat(count("SELECT COUNT(*) FROM order_reminders WHERE order_id = ?", booking.order().id()))
				.isZero();

		reset(gateway);
		assertThat(service.sendDueReminders()).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM order_reminders WHERE order_id = ?", booking.order().id()))
				.isEqualTo(1);
	}

	@Test
	void adminsReadAndChangeTheSettingsOverHttp() throws Exception {
		Booking booking = book(Duration.ofHours(20), "http@example.com");
		String url = "/api/v1/admin/events/{id}/reminder";

		mockMvc.perform(get(url, booking.event().id()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.hoursBefore").value(24))
				.andExpect(jsonPath("$.usingDefault").value(true))
				.andExpect(jsonPath("$.remindersSent").value(0));

		mockMvc.perform(put(url, booking.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"enabled\":true,\"hoursBefore\":72}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hoursBefore").value(72))
				.andExpect(jsonPath("$.usingDefault").value(false))
				.andExpect(jsonPath("$.remindAt").isNotEmpty());

		assertThat(count("SELECT COUNT(*) FROM event_reminder_settings WHERE updated_by = ?", admin.id()))
				.isEqualTo(1);
	}

	@Test
	void reportsHowManyRemindersWentOut() throws Exception {
		Booking booking = book(Duration.ofHours(20), "count@example.com");
		service.sendDueReminders();

		mockMvc.perform(get("/api/v1/admin/events/{id}/reminder", booking.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.remindersSent").value(1));
	}

	@Test
	void rejectsHoursOutsideTheAllowedRange() throws Exception {
		Booking booking = book(Duration.ofHours(20), "range@example.com");

		for (String hours : List.of("0", "337", "-5", "null")) {
			mockMvc.perform(put("/api/v1/admin/events/{id}/reminder", booking.event().id())
							.header(HttpHeaders.AUTHORIZATION, bearer(admin))
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"enabled\":true,\"hoursBefore\":%s}".formatted(hours)))
					.andExpect(status().isBadRequest());
		}
		assertThat(count("SELECT COUNT(*) FROM event_reminder_settings")).isZero();
	}

	@Test
	void onlyAdminsCanSeeOrChangeTheSettings() throws Exception {
		Booking booking = book(Duration.ofHours(20), "who@example.com");
		UserRecord buyer = booking.buyer();

		mockMvc.perform(get("/api/v1/admin/events/{id}/reminder", booking.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/admin/events/{id}/reminder", booking.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(buyer))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"enabled\":false,\"hoursBefore\":24}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/admin/events/{id}/reminder", booking.event().id()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unknownAndCancelledEventsAreRefused() throws Exception {
		Booking booking = book(Duration.ofHours(20), "gone@example.com");
		cancellationService.cancelEvent(booking.event().id(), admin.id(), EventCancellationRequest.NONE);

		mockMvc.perform(get("/api/v1/admin/events/{id}/reminder", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/v1/admin/events/{id}/reminder", booking.event().id())
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"enabled\":true,\"hoursBefore\":24}"))
				.andExpect(status().isConflict());
	}

	private void saveSettings(EventRecord event, boolean enabled, int hours) {
		reminderMapper.upsertSettings(event.id(), enabled, hours, admin.id(), Instant.now());
	}

	/** A paid order for an event that starts in {@code startsIn}, placed well before any reminder window. */
	private Booking book(Duration startsIn, String email) {
		EventRecord event = insertPublishedEvent(startsIn);
		UserRecord buyer = insertUser(email, UserRole.USER);
		Instant now = Instant.now();
		ReservationRecord reservation = new ReservationRecord(
				UUID.randomUUID(), buyer.id(), event.id(), UUID.randomUUID(), ReservationStatus.PENDING_PAYMENT,
				now.plusSeconds(60), new BigDecimal("1.00"), now.minusSeconds(60), now.minusSeconds(60));
		reservationMapper.insert(reservation);
		reservationItemMapper.batchInsert(List.of(new ReservationItemRecord(
				UUID.randomUUID(), reservation.id(), eventSeatMapper.findByEventId(event.id()).getFirst().id(),
				new BigDecimal("100000"), now.minusSeconds(60))));
		OrderResponse order = orderService.createOrder(buyer.id(), new OrderCreateRequest(reservation.id()));
		paymentService.createPayment(order.id(), buyer.id(), new PaymentCreateRequest("tok_success"));
		// The purchase happened days ago, so it predates every reminder window these tests use.
		jdbcTemplate.update(
				"UPDATE orders SET created_at = NOW() - INTERVAL '10 days', "
						+ "updated_at = NOW() - INTERVAL '10 days' WHERE id = ?",
				order.id());
		return new Booking(event, buyer, order);
	}

	private EventRecord insertPublishedEvent(Duration startsIn) {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(), "Reminder Hall", "1 Reminder Street", "Ho Chi Minh City", "Vietnam",
				"Asia/Ho_Chi_Minh");
		venueMapper.insert(venue);
		VenueSectionRecord section = VenueSectionRecord.forInsert(UUID.randomUUID(), venue.id(), "Orchestra", 1);
		sectionMapper.insert(section);
		seatMapper.insert(SeatRecord.forInsert(UUID.randomUUID(), section.id(), "A", 1, "A1", false));
		Instant now = Instant.now();
		EventRecord event = EventRecord.forInsert(
				UUID.randomUUID(), venue.id(), "Reminder Event %s".formatted(UUID.randomUUID()),
				"Reminder integration test", now.plus(Duration.ofDays(2)), now.minus(Duration.ofHours(1)),
				now.plus(Duration.ofHours(2)));
		eventMapper.insert(event);
		eventSectionMapper.insertForDraftEvent(EventSectionRecord.forInsert(
				UUID.randomUUID(), event.id(), section.id(), new BigDecimal("125000.00"), true));
		eventSeatMapper.insertForDraftEvent(event.id());
		jdbcTemplate.update(
				"UPDATE events SET status = 'PUBLISHED', start_time = ?, sales_start_time = ?, sales_end_time = ? "
						+ "WHERE id = ?",
				java.sql.Timestamp.from(now.plus(startsIn)),
				java.sql.Timestamp.from(now.minus(Duration.ofDays(30))),
				java.sql.Timestamp.from(now.plus(startsIn).minus(Duration.ofMinutes(30))),
				event.id());
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

	private int count(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
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

	private record Booking(EventRecord event, UserRecord buyer, OrderResponse order) {
	}
}
