package com.seatflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

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
import com.seatflow.kafka.EventEnvelope;
import com.seatflow.consumer.ProcessedEventMapper;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.order.OrderCreateRequest;
import com.seatflow.order.OrderResponse;
import com.seatflow.order.OrderService;
import com.seatflow.payment.PaymentCreateRequest;
import com.seatflow.payment.PaymentService;
import com.seatflow.promo.PromoCodeCreateRequest;
import com.seatflow.promo.PromoCodeService;
import com.seatflow.promo.PromoDiscountType;
import com.seatflow.refund.RefundMapper;
import com.seatflow.refund.RefundRequest;
import com.seatflow.refund.RefundService;
import com.seatflow.reservation.ReservationItemMapper;
import com.seatflow.reservation.ReservationItemRecord;
import com.seatflow.reservation.ReservationMapper;
import com.seatflow.reservation.ReservationRecord;
import com.seatflow.reservation.ReservationStatus;
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

/** Runs the real queries against Postgres, with the mail gateway replaced so the messages can be read. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OrderEmailNotificationIntegrationTests extends PostgresTestContainerSupport {

	@Autowired
	private ProcessedEventMapper processedEventMapper;

	@Autowired
	private OrderNotificationMapper notificationMapper;

	@Autowired
	private RefundMapper refundMapper;

	@Autowired
	private OrderEmailTemplates templates;

	@Autowired
	private BusinessMetrics businessMetrics;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private Clock clock;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OrderService orderService;

	@Autowired
	private PaymentService paymentService;

	@Autowired
	private RefundService refundService;

	@Autowired
	private PromoCodeService promoCodeService;

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

	@MockitoBean
	private SeatHoldStore seatHoldStore;

	// Built by hand with a mock gateway so the test shares the application context of the other
	// integration tests; the transaction template stands in for the @Transactional proxy.
	private final EmailGateway emailGateway = mock(EmailGateway.class);

	private OrderEmailNotificationConsumer consumer;

	private UserRecord admin;

	@BeforeEach
	void setUp() {
		consumer = new OrderEmailNotificationConsumer(
				processedEventMapper, notificationMapper, refundMapper, templates, emailGateway, businessMetrics,
				objectMapper, clock);
		cleanDatabase();
		reset(emailGateway);
		when(seatHoldStore.isHoldActive(any(SeatHoldRecord.class))).thenReturn(true);
		admin = insertUser("admin@example.com", UserRole.ADMIN);
	}

	@AfterEach
	void tearDown() {
		cleanDatabase();
	}

	@Test
	void aPaidOrderEmailsTheBuyerTheirSeatsAndTotal() {
		Scenario scenario = pay("buyer@example.com", "125000", "75000");

		assertThat(process(paid(scenario.order().id()))).isTrue();

		EmailMessage message = onlyMessage();
		assertThat(message.to()).isEqualTo("buyer@example.com");
		assertThat(message.subject()).isEqualTo("Your tickets for " + scenario.event().name());
		assertThat(message.text())
				.contains("Where: Email Hall, 1 Email Street, Ho Chi Minh City")
				.contains("- Orchestra, row A, seat A1")
				.contains("- Orchestra, row A, seat A2")
				.contains("Total paid: 200,000 VND");
	}

	@Test
	void processingTheSameEventTwiceSendsOneEmail() {
		Scenario scenario = pay("repeat@example.com", "100000");
		EventEnvelope<Map<String, Object>> event = paid(scenario.order().id());

		assertThat(process(event)).isTrue();
		assertThat(process(event)).isFalse();

		verify(emailGateway, times(1)).send(any());
		assertThat(count("SELECT COUNT(*) FROM processed_events WHERE consumer_name = 'order-email-notifications'"))
				.isEqualTo(1);
	}

	@Test
	void aFailedSendLeavesNoClaimBehindSoTheRetryStillSends() {
		Scenario scenario = pay("flaky@example.com", "100000");
		EventEnvelope<Map<String, Object>> event = paid(scenario.order().id());
		doThrow(new EmailDeliveryException("smtp down", new RuntimeException())).when(emailGateway).send(any());

		assertThatThrownBy(() -> process(event)).isInstanceOf(EmailDeliveryException.class);

		assertThat(count("SELECT COUNT(*) FROM processed_events WHERE consumer_name = 'order-email-notifications'"))
				.isZero();

		reset(emailGateway);
		assertThat(process(event)).isTrue();
		verify(emailGateway, times(1)).send(any());
		assertThat(count("SELECT COUNT(*) FROM processed_events WHERE consumer_name = 'order-email-notifications'"))
				.isEqualTo(1);
	}

	@Test
	void anAdminRefundEmailsAReceiptWithTheReason() {
		Scenario scenario = pay("refund@example.com", "125000", "75000");
		refundService.refundOrder(scenario.order().id(), admin.id(), new RefundRequest("Duplicate purchase"));

		assertThat(process(refunded(scenario.order().id()))).isTrue();

		EmailMessage message = onlyMessage();
		assertThat(message.subject()).isEqualTo("Your refund for " + scenario.event().name());
		assertThat(message.text())
				.contains("Reason: Duplicate purchase")
				.contains("Amount refunded: 200,000 VND")
				.contains("- Orchestra, row A, seat A1");
	}

	@Test
	void aRefundFromACancelledEventApologisesAndGivesTheReason() {
		Scenario scenario = pay("cancelled@example.com", "100000");
		cancellationService.cancelEvent(
				scenario.event().id(), admin.id(), new EventCancellationRequest("Venue flooded"));

		assertThat(process(refunded(scenario.order().id()))).isTrue();

		EmailMessage message = onlyMessage();
		assertThat(message.subject()).isEqualTo(scenario.event().name() + " has been cancelled - you have been refunded");
		assertThat(message.text())
				.contains("Reason: Venue flooded")
				.contains("refunded in full (100,000 VND)");
	}

	@Test
	void aPromoDiscountIsShownOnTheConfirmation() {
		promoCodeService.create(new PromoCodeCreateRequest(
				"SPRING", PromoDiscountType.FIXED, new BigDecimal("20000"), null, null, 5));
		Scenario scenario = payWithPromo("promo@example.com", "SPRING", "100000");
		process(paid(scenario.order().id()));

		assertThat(onlyMessage().text())
				.contains("Subtotal: 100,000 VND")
				.contains("Discount (SPRING): -20,000 VND")
				.contains("Total paid: 80,000 VND");
	}

	private boolean process(EventEnvelope<?> event) {
		return Boolean.TRUE.equals(transactionTemplate.execute(status -> consumer.process(event)));
	}

	private EmailMessage onlyMessage() {
		ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
		verify(emailGateway).send(captor.capture());
		return captor.getValue();
	}

	private static EventEnvelope<Map<String, Object>> paid(UUID orderId) {
		return envelope("OrderPaid", orderId);
	}

	private static EventEnvelope<Map<String, Object>> refunded(UUID orderId) {
		return envelope("OrderRefunded", orderId);
	}

	private static EventEnvelope<Map<String, Object>> envelope(String type, UUID orderId) {
		return new EventEnvelope<>(
				UUID.randomUUID(), type, 1, orderId, UUID.randomUUID(), Instant.now(),
				Map.of("orderId", orderId.toString()));
	}

	private Scenario pay(String email, String... prices) {
		return payWithPromo(email, null, prices);
	}

	private Scenario payWithPromo(String email, String promoCode, String... prices) {
		EventRecord event = insertEventWithSeats(prices.length);
		List<EventSeatRecord> seats = eventSeatMapper.findByEventId(event.id());
		UserRecord user = insertUser(email, UserRole.USER);
		Instant now = Instant.now();
		ReservationRecord reservation = new ReservationRecord(
				UUID.randomUUID(), user.id(), event.id(), UUID.randomUUID(), ReservationStatus.PENDING_PAYMENT,
				now.plusSeconds(60), new BigDecimal("1.00"), now.minusSeconds(60), now.minusSeconds(60));
		reservationMapper.insert(reservation);
		List<ReservationItemRecord> items = java.util.stream.IntStream.range(0, prices.length)
				.mapToObj(index -> new ReservationItemRecord(
						UUID.randomUUID(), reservation.id(), seats.get(index).id(),
						new BigDecimal(prices[index]), now.minusSeconds(60)))
				.toList();
		reservationItemMapper.batchInsert(items);
		OrderResponse order = orderService.createOrder(user.id(), new OrderCreateRequest(reservation.id(), promoCode));
		paymentService.createPayment(order.id(), user.id(), new PaymentCreateRequest("tok_success"));
		return new Scenario(event, order);
	}

	private EventRecord insertEventWithSeats(int seatCount) {
		VenueRecord venue = VenueRecord.forInsert(
				UUID.randomUUID(), "Email Hall", "1 Email Street", "Ho Chi Minh City", "Vietnam",
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
				UUID.randomUUID(), venue.id(), "Email Event %s".formatted(UUID.randomUUID()),
				"Email integration test", now.plus(Duration.ofDays(1)), now.minus(Duration.ofHours(1)),
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

	private int count(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

	private void cleanDatabase() {
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

	private record Scenario(EventRecord event, OrderResponse order) {
	}
}
