package com.seatflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.seatflow.event.EventSeatStatus;
import com.seatflow.event.EventSectionMapper;
import com.seatflow.event.EventSectionRecord;
import com.seatflow.hold.SeatHoldRecord;
import com.seatflow.hold.SeatHoldStore;
import com.seatflow.idempotency.IdempotencyMapper;
import com.seatflow.idempotency.IdempotencyOperation;
import com.seatflow.idempotency.IdempotencyRecord;
import com.seatflow.maintenance.MaintenanceService;
import com.seatflow.maintenance.MaintenanceSummary;
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
	private MaintenanceService maintenanceService;

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
		assertThat(countRows("tickets")).isEqualTo(2);
		assertOrderPaidOutboxEvent(fixture, paymentId, 2);
		verify(seatHoldStore).releaseHold(any(SeatHoldRecord.class));
	}

	@ParameterizedTest
	@CsvSource({
			"tok_declined, DECLINED, Payment declined",
			"tok_timeout, TIMED_OUT, Payment timed out",
			"tok_error, FAILED, Simulated provider error"
	})
	void failedPaymentOutcomesLeaveSeatsAvailableAndTheOrderRetryable(
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

		// A decline or timeout is not terminal: the customer still holds the seats, so the
		// order has to stay payable for as long as that hold lasts.
		assertThat(orderMapper.findByIdAndUser(fixture.order().id(), fixture.user().id()).status())
				.isEqualTo(OrderStatus.PENDING);
		assertThat(reservationMapper.findByIdAndUser(fixture.reservation().id(), fixture.user().id()).status())
				.isEqualTo(ReservationStatus.PENDING_PAYMENT);
		assertThat(eventSeatMapper.findByEventId(fixture.inventory().event().id()))
				.allSatisfy(seat -> {
					assertThat(seat.permanentStatus()).isEqualTo(EventSeatStatus.AVAILABLE);
					assertThat(seat.version()).isZero();
				});
		assertThat(countRows("outbox_events")).isZero();
		verify(seatHoldStore, never()).releaseHold(any(SeatHoldRecord.class));
	}

	@Test
	void retryAfterADeclineCanStillCompleteThePurchase() throws Exception {
		PaymentFixture fixture = insertFixture(
				"payment-retry-after-decline@example.com",
				List.of(new BigDecimal("125000.00")));

		createPayment(fixture, "tok_declined")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("DECLINED"));

		// A new idempotency key is a genuinely new attempt, and the seats are still held.
		createPayment(fixture, "tok_success")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"));

		assertThat(orderMapper.findByIdAndUser(fixture.order().id(), fixture.user().id()).status())
				.isEqualTo(OrderStatus.PAID);
		assertThat(reservationMapper.findByIdAndUser(fixture.reservation().id(), fixture.user().id()).status())
				.isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(countRows("payments")).isEqualTo(2);
		assertThat(countRows("tickets")).isEqualTo(1);
	}

	@Test
	void maintenanceSweepClosesAnAbandonedCheckoutAsCancelled() {
		PaymentFixture fixture = insertFixture(
				"payment-sweep-abandoned@example.com",
				List.of(new BigDecimal("125000.00")));
		lapseHoldWindow(fixture);

		MaintenanceSummary summary = maintenanceService.sweep();

		assertThat(summary.expiredReservations()).isEqualTo(1);
		assertThat(summary.closedOrders()).isEqualTo(1);
		assertThat(reservationMapper.findByIdAndUser(fixture.reservation().id(), fixture.user().id()).status())
				.isEqualTo(ReservationStatus.EXPIRED);
		assertThat(orderMapper.findByIdAndUser(fixture.order().id(), fixture.user().id()).status())
				.isEqualTo(OrderStatus.CANCELLED);
	}

	@Test
	void maintenanceSweepClosesACheckoutThatFailedPaymentAsFailed() throws Exception {
		PaymentFixture fixture = insertFixture(
				"payment-sweep-declined@example.com",
				List.of(new BigDecimal("125000.00")));
		createPayment(fixture, "tok_declined").andExpect(status().isCreated());
		lapseHoldWindow(fixture);

		maintenanceService.sweep();

		// An abandoned checkout and one that actually tried to pay stay tellable apart.
		assertThat(reservationMapper.findByIdAndUser(fixture.reservation().id(), fixture.user().id()).status())
				.isEqualTo(ReservationStatus.PAYMENT_FAILED);
		assertThat(orderMapper.findByIdAndUser(fixture.order().id(), fixture.user().id()).status())
				.isEqualTo(OrderStatus.FAILED);
	}

	@Test
	void maintenanceSweepLeavesALiveCheckoutAlone() {
		PaymentFixture fixture = insertFixture(
				"payment-sweep-live@example.com",
				List.of(new BigDecimal("125000.00")));

		MaintenanceSummary summary = maintenanceService.sweep();

		assertThat(summary.expiredReservations()).isZero();
		assertThat(summary.closedOrders()).isZero();
		assertThat(orderMapper.findByIdAndUser(fixture.order().id(), fixture.user().id()).status())
				.isEqualTo(OrderStatus.PENDING);
	}

	@Test
	void maintenanceSweepLeavesAPaidOrderAlone() throws Exception {
		PaymentFixture fixture = insertFixture(
				"payment-sweep-paid@example.com",
				List.of(new BigDecimal("125000.00")));
		createPayment(fixture, "tok_success").andExpect(status().isCreated());
		lapseHoldWindow(fixture);

		maintenanceService.sweep();

		assertThat(reservationMapper.findByIdAndUser(fixture.reservation().id(), fixture.user().id()).status())
				.isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(orderMapper.findByIdAndUser(fixture.order().id(), fixture.user().id()).status())
				.isEqualTo(OrderStatus.PAID);
	}

	private void lapseHoldWindow(PaymentFixture fixture) {
		assertThat(jdbcTemplate.update(
				"UPDATE reservations SET expires_at = created_at WHERE id = ?",
				fixture.reservation().id())).isEqualTo(1);
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
				.andExpect(jsonPath("$.title").value("Order not found"));

		assertThat(countRows("payments")).isZero();
		assertThat(countRows("outbox_events")).isZero();
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
				.andExpect(jsonPath("$.title").value("Payment conflict"));

		assertThat(countRows("payments")).isEqualTo(1);
		assertThat(countRows("tickets")).isEqualTo(1);
		assertThat(countRows("outbox_events")).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = 'SUCCEEDED'",
				Integer.class,
				fixture.order().id())).isEqualTo(1);
		verify(seatHoldStore).releaseHold(any(SeatHoldRecord.class));
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
		assertThat(countRows("tickets")).isEqualTo(1);
		assertThat(countRows("idempotency_records")).isEqualTo(1);
		assertThat(countRows("outbox_events")).isEqualTo(1);
		IdempotencyRecord record = idempotencyMapper.findByScope(
				fixture.user().id(),
				IdempotencyOperation.CREATE_PAYMENT,
				idempotencyKey);
		assertThat(record.responseStatus()).isEqualTo(201);
		assertThat(record.requestHash()).hasSize(64).doesNotContain("tok_success");
		assertThat(record.responseBody()).doesNotContain("tok_success");
		verify(seatHoldStore).releaseHold(any(SeatHoldRecord.class));
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
			assertThat(countRows("tickets")).isEqualTo(1);
			assertThat(countRows("idempotency_records")).isEqualTo(1);
			assertThat(countRows("outbox_events")).isEqualTo(1);
			verify(seatHoldStore).releaseHold(any(SeatHoldRecord.class));
		}
		finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void competingBuyersLockSeatsInTheSameOrderAndOnlyOnePurchaseCommits() throws Exception {
		EventInventory inventory = insertEventInventory(2);
		PaymentFixture firstBuyer = insertFixture(
				inventory,
				"payment-race-first@example.com",
				List.of(new BigDecimal("125000.00"), new BigDecimal("85000.00")));
		PaymentFixture secondBuyer = insertFixture(
				new EventInventory(inventory.event(), inventory.eventSeats().reversed()),
				"payment-race-second@example.com",
				List.of(new BigDecimal("85000.00"), new BigDecimal("125000.00")));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<PurchaseAttempt> first = executor.submit(() -> purchaseWhenReleased(
					firstBuyer,
					"race-first",
					ready,
					start));
			Future<PurchaseAttempt> second = executor.submit(() -> purchaseWhenReleased(
					secondBuyer,
					"race-second",
					ready,
					start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<PurchaseAttempt> attempts = List.of(
					first.get(10, TimeUnit.SECONDS),
					second.get(10, TimeUnit.SECONDS));
			List<PurchaseAttempt> successful = attempts.stream()
					.filter(attempt -> attempt.result() != null)
					.toList();
			List<PurchaseAttempt> failed = attempts.stream()
					.filter(attempt -> attempt.failure() != null)
					.toList();

			assertThat(successful).hasSize(1);
			assertThat(failed).singleElement()
					.extracting(PurchaseAttempt::failure)
					.isInstanceOf(PaymentConflictException.class);
			PaymentFixture winner = successful.getFirst().fixture();
			PaymentFixture loser = winner.order().id().equals(firstBuyer.order().id())
					? secondBuyer
					: firstBuyer;

			assertThat(countRows("payments")).isEqualTo(1);
			assertThat(countRows("tickets")).isEqualTo(2);
			assertThat(countRows("idempotency_records")).isEqualTo(1);
			assertThat(countRows("outbox_events")).isEqualTo(1);
			assertThat(eventSeatMapper.findByEventId(inventory.event().id()))
					.allSatisfy(seat -> assertThat(seat.permanentStatus()).isEqualTo(EventSeatStatus.SOLD));
			assertThat(orderMapper.findByIdAndUser(winner.order().id(), winner.user().id()).status())
					.isEqualTo(OrderStatus.PAID);
			assertThat(reservationMapper.findByIdAndUser(
					winner.reservation().id(), winner.user().id()).status())
					.isEqualTo(ReservationStatus.CONFIRMED);
			assertThat(orderMapper.findByIdAndUser(loser.order().id(), loser.user().id()).status())
					.isEqualTo(OrderStatus.PENDING);
			assertThat(reservationMapper.findByIdAndUser(
					loser.reservation().id(), loser.user().id()).status())
					.isEqualTo(ReservationStatus.PENDING_PAYMENT);
			verify(seatHoldStore).releaseHold(any(SeatHoldRecord.class));
		}
		finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void failedSeatUpdateRollsBackTheEntireMultiSeatPurchase() throws Exception {
		PaymentFixture fixture = insertFixture(
				"payment-multi-seat-rollback@example.com",
				List.of(new BigDecimal("125000.00"), new BigDecimal("85000.00")));
		UUID secondLockedSeatId = fixture.inventory().eventSeats().stream()
				.map(EventSeatRecord::id)
				.sorted((left, right) -> left.toString().compareTo(right.toString()))
				.toList()
				.getLast();
		jdbcTemplate.execute("""
				CREATE OR REPLACE FUNCTION seatflow_test_skip_event_seat_sale()
				RETURNS trigger AS $$
				BEGIN
				    IF OLD.id = '%s'::uuid AND NEW.permanent_status = 'SOLD' THEN
				        RETURN NULL;
				    END IF;
				    RETURN NEW;
				END;
				$$ LANGUAGE plpgsql
				""".formatted(secondLockedSeatId));
		jdbcTemplate.execute("""
				CREATE TRIGGER seatflow_test_skip_event_seat_sale_trigger
				BEFORE UPDATE ON event_seats
				FOR EACH ROW EXECUTE FUNCTION seatflow_test_skip_event_seat_sale()
				""");

		try {
			createPayment(fixture, "tok_success")
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.title").value("Payment conflict"));
		}
		finally {
			jdbcTemplate.execute("DROP TRIGGER IF EXISTS seatflow_test_skip_event_seat_sale_trigger ON event_seats");
			jdbcTemplate.execute("DROP FUNCTION IF EXISTS seatflow_test_skip_event_seat_sale()");
		}

		assertThat(eventSeatMapper.findByEventId(fixture.inventory().event().id()))
				.allSatisfy(seat -> {
					assertThat(seat.permanentStatus()).isEqualTo(EventSeatStatus.AVAILABLE);
					assertThat(seat.version()).isZero();
				});
		assertThat(orderMapper.findByIdAndUser(fixture.order().id(), fixture.user().id()).status())
				.isEqualTo(OrderStatus.PENDING);
		assertThat(reservationMapper.findByIdAndUser(fixture.reservation().id(), fixture.user().id()).status())
				.isEqualTo(ReservationStatus.PENDING_PAYMENT);
		assertThat(countRows("payments")).isZero();
		assertThat(countRows("tickets")).isZero();
		assertThat(countRows("idempotency_records")).isZero();
		assertThat(countRows("outbox_events")).isZero();
		verify(seatHoldStore, never()).releaseHold(any(SeatHoldRecord.class));
	}

	@Test
	void outboxInsertFailureRollsBackThePurchaseTransaction() throws Exception {
		PaymentFixture fixture = insertFixture(
				"payment-outbox-rollback@example.com",
				List.of(new BigDecimal("125000.00")));
		jdbcTemplate.execute("""
				CREATE OR REPLACE FUNCTION seatflow_test_reject_outbox_insert()
				RETURNS trigger AS $$
				BEGIN
				    RAISE EXCEPTION 'outbox unavailable';
				END;
				$$ LANGUAGE plpgsql
				""");
		jdbcTemplate.execute("""
				CREATE TRIGGER seatflow_test_reject_outbox_insert_trigger
				BEFORE INSERT ON outbox_events
				FOR EACH ROW EXECUTE FUNCTION seatflow_test_reject_outbox_insert()
				""");

		try {
			// A write the database refuses is classified as a persistence failure, not as a
			// generic unexpected one - same 500, but the more specific of the two.
			createPayment(fixture, "tok_success")
					.andExpect(status().isInternalServerError())
					.andExpect(jsonPath("$.title").value("Persistence error"));
		}
		finally {
			jdbcTemplate.execute("DROP TRIGGER IF EXISTS seatflow_test_reject_outbox_insert_trigger ON outbox_events");
			jdbcTemplate.execute("DROP FUNCTION IF EXISTS seatflow_test_reject_outbox_insert()");
		}

		assertThat(eventSeatMapper.findByEventId(fixture.inventory().event().id()))
				.singleElement()
				.satisfies(seat -> {
					assertThat(seat.permanentStatus()).isEqualTo(EventSeatStatus.AVAILABLE);
					assertThat(seat.version()).isZero();
				});
		assertThat(orderMapper.findByIdAndUser(fixture.order().id(), fixture.user().id()).status())
				.isEqualTo(OrderStatus.PENDING);
		assertThat(reservationMapper.findByIdAndUser(fixture.reservation().id(), fixture.user().id()).status())
				.isEqualTo(ReservationStatus.PENDING_PAYMENT);
		assertThat(countRows("payments")).isZero();
		assertThat(countRows("tickets")).isZero();
		assertThat(countRows("idempotency_records")).isZero();
		assertThat(countRows("outbox_events")).isZero();
		verify(seatHoldStore, never()).releaseHold(any(SeatHoldRecord.class));
	}

	@Test
	void redisReleaseFailureDoesNotUndoCommittedPurchase() throws Exception {
		PaymentFixture fixture = insertFixture(
				"payment-redis-release-failure@example.com",
				List.of(new BigDecimal("125000.00")));
		doThrow(new IllegalStateException("Redis unavailable"))
				.when(seatHoldStore)
				.releaseHold(any(SeatHoldRecord.class));

		createPayment(fixture, "tok_success")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"));

		assertThat(orderMapper.findByIdAndUser(fixture.order().id(), fixture.user().id()).status())
				.isEqualTo(OrderStatus.PAID);
		assertThat(reservationMapper.findByIdAndUser(fixture.reservation().id(), fixture.user().id()).status())
				.isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(eventSeatMapper.findByEventId(fixture.inventory().event().id()))
				.singleElement()
				.extracting(EventSeatRecord::permanentStatus)
				.isEqualTo(EventSeatStatus.SOLD);
		assertThat(countRows("payments")).isEqualTo(1);
		assertThat(countRows("tickets")).isEqualTo(1);
		assertThat(countRows("idempotency_records")).isEqualTo(1);
		assertThat(countRows("outbox_events")).isEqualTo(1);
		verify(seatHoldStore).releaseHold(any(SeatHoldRecord.class));
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
				.andExpect(jsonPath("$.title").value("Idempotency key conflict"));

		assertThat(countRows("payments")).isEqualTo(1);
		assertThat(countRows("idempotency_records")).isEqualTo(1);
		assertThat(countRows("outbox_events")).isZero();
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
		assertThat(countRows("outbox_events")).isEqualTo(2);
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
				.andExpect(jsonPath("$.title").value("Idempotency-Key header is required"));

		assertThat(countRows("payments")).isZero();
		assertThat(countRows("idempotency_records")).isZero();
		assertThat(countRows("outbox_events")).isZero();
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

	private PurchaseAttempt purchaseWhenReleased(
			PaymentFixture fixture,
			String idempotencyKey,
			CountDownLatch ready,
			CountDownLatch start) throws InterruptedException {
		ready.countDown();
		start.await();
		try {
			IdempotentPaymentResult result = paymentIdempotencyService.createPayment(
					fixture.order().id(),
					fixture.user().id(),
					idempotencyKey,
					new PaymentCreateRequest("tok_success"));
			return new PurchaseAttempt(fixture, result, null);
		}
		catch (RuntimeException ex) {
			return new PurchaseAttempt(fixture, null, ex);
		}
	}

	private PaymentFixture insertFixture(String email, List<BigDecimal> prices) {
		EventInventory inventory = insertEventInventory(prices.size());
		return insertFixture(inventory, email, prices);
	}

	private PaymentFixture insertFixture(
			EventInventory inventory,
			String email,
			List<BigDecimal> prices) {
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

	private void assertOrderPaidOutboxEvent(PaymentFixture fixture, UUID paymentId, int seatCount) throws Exception {
		JsonNode event = objectMapper.readTree(jdbcTemplate.queryForObject(
				"""
				SELECT jsonb_build_object(
				    'aggregateType', aggregate_type,
				    'aggregateId', aggregate_id,
				    'eventType', event_type,
				    'eventVersion', event_version,
				    'payload', payload,
				    'correlationId', correlation_id,
				    'status', status,
				    'attemptCount', attempt_count,
				    'publishedAt', published_at,
				    'nextAttemptAt', next_attempt_at
				)::text
				FROM outbox_events
				WHERE aggregate_id = ?
				""",
				String.class,
				fixture.order().id()));

		assertThat(event.get("aggregateType").asText()).isEqualTo("Order");
		assertThat(event.get("aggregateId").asText()).isEqualTo(fixture.order().id().toString());
		assertThat(event.get("eventType").asText()).isEqualTo("OrderPaid");
		assertThat(event.get("eventVersion").asInt()).isEqualTo(1);
		assertThat(event.get("correlationId").asText()).isEqualTo(paymentId.toString());
		assertThat(event.get("status").asText()).isEqualTo("PENDING");
		assertThat(event.get("attemptCount").asInt()).isZero();
		assertThat(event.get("publishedAt").isNull()).isTrue();
		assertThat(event.get("nextAttemptAt").isNull()).isFalse();
		assertThat(event.get("payload").get("orderId").asText()).isEqualTo(fixture.order().id().toString());
		assertThat(event.get("payload").get("reservationId").asText())
				.isEqualTo(fixture.reservation().id().toString());
		assertThat(event.get("payload").get("userId").asText()).isEqualTo(fixture.user().id().toString());
		assertThat(event.get("payload").get("paymentId").asText()).isEqualTo(paymentId.toString());
		assertThat(event.get("payload").get("eventSeatIds").size()).isEqualTo(seatCount);
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

	private record PurchaseAttempt(
			PaymentFixture fixture,
			IdempotentPaymentResult result,
			RuntimeException failure) {
	}
}
