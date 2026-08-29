package com.seatflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.seatflow.event.EventSeatMapper;
import com.seatflow.event.EventSeatRecord;
import com.seatflow.event.EventSeatStatus;
import com.seatflow.hold.SeatHoldRecord;
import com.seatflow.hold.SeatHoldStore;
import com.seatflow.order.OrderMapper;
import com.seatflow.order.OrderNotFoundException;
import com.seatflow.order.OrderRecord;
import com.seatflow.order.OrderStatus;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.outbox.OutboxService;
import com.seatflow.outbox.OutboxStorageException;
import com.seatflow.reservation.ReservationItemMapper;
import com.seatflow.reservation.ReservationItemRecord;
import com.seatflow.reservation.ReservationMapper;
import com.seatflow.reservation.ReservationRecord;
import com.seatflow.reservation.ReservationStatus;
import com.seatflow.ticket.TicketService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
	private static final UUID USER_ID = UUID.fromString("0abcc6a7-eb30-46aa-be8b-677811650fe4");
	private static final UUID OTHER_USER_ID = UUID.fromString("c5405e14-0d4b-4db0-b598-65698b8d6748");
	private static final UUID ORDER_ID = UUID.fromString("2ae13b6b-df9b-4e08-99ae-f6b13f1997b0");
	private static final UUID RESERVATION_ID = UUID.fromString("db84eccc-adb4-499a-aef5-8090035ca183");
	private static final UUID PAYMENT_ID = UUID.fromString("fd9e706d-a502-41ae-9012-a20e24625333");
	private static final UUID EVENT_ID = UUID.fromString("3eaebbe0-d542-492c-b306-99ee777daf00");
	private static final UUID HOLD_ID = UUID.fromString("8142c8b8-a67d-4901-a8bc-0a08c0c55c48");
	private static final UUID EVENT_SEAT_ID_1 = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final UUID EVENT_SEAT_ID_2 = UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final UUID SEAT_ID_1 = UUID.fromString("31111111-1111-4111-8111-111111111111");
	private static final UUID SEAT_ID_2 = UUID.fromString("32222222-2222-4222-8222-222222222222");

	@Mock
	private PaymentMapper paymentMapper;

	@Mock
	private OrderMapper orderMapper;

	@Mock
	private ReservationMapper reservationMapper;

	@Mock
	private ReservationItemMapper reservationItemMapper;

	@Mock
	private EventSeatMapper eventSeatMapper;

	@Mock
	private TicketService ticketService;

	@Mock
	private SeatHoldStore seatHoldStore;

	@Mock
	private OutboxService outboxService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private SimpleMeterRegistry meterRegistry;

	private PaymentService paymentService;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		paymentService = new PaymentService(
				paymentMapper,
				orderMapper,
				reservationMapper,
				reservationItemMapper,
				eventSeatMapper,
				ticketService,
				seatHoldStore,
				outboxService,
				eventPublisher,
				Clock.fixed(NOW, ZoneOffset.UTC),
				new BusinessMetrics(meterRegistry));
	}

	@Test
	void successLocksInOrderPaysAndPublishesHoldRelease() {
		stubPendingPayment(PaymentStatus.SUCCEEDED, null);
		when(reservationMapper.updateStatus(
				RESERVATION_ID,
				ReservationStatus.PENDING_PAYMENT,
				ReservationStatus.CONFIRMED,
				NOW)).thenReturn(1);
		when(eventSeatMapper.markSold(any())).thenReturn(1);

		PaymentResponse response = paymentService.createPayment(
				ORDER_ID,
				USER_ID,
				new PaymentCreateRequest("tok_success"));

		assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
		assertThat(response.amount()).isEqualByComparingTo("210000.75");
		verify(eventSeatMapper).lockByIds(List.of(EVENT_SEAT_ID_1, EVENT_SEAT_ID_2));
		verify(orderMapper).updateStatus(ORDER_ID, USER_ID, OrderStatus.PENDING, OrderStatus.PAID, NOW);
		verify(eventSeatMapper).markSold(EVENT_SEAT_ID_1);
		verify(eventSeatMapper).markSold(EVENT_SEAT_ID_2);
		verify(ticketService).issueTickets(ORDER_ID, List.of(EVENT_SEAT_ID_1, EVENT_SEAT_ID_2), NOW);
		verify(outboxService).recordOrderPaid(
				order(OrderStatus.PENDING),
				payment(PaymentStatus.SUCCEEDED, null),
				List.of(eventSeat(EVENT_SEAT_ID_1, SEAT_ID_1), eventSeat(EVENT_SEAT_ID_2, SEAT_ID_2)),
				NOW);

		ArgumentCaptor<SeatHoldReleaseRequested> eventCaptor =
				ArgumentCaptor.forClass(SeatHoldReleaseRequested.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue().hold().holdId()).isEqualTo(HOLD_ID);
		assertThat(eventCaptor.getValue().hold().eventSeatIds())
				.containsExactly(EVENT_SEAT_ID_1, EVENT_SEAT_ID_2);
		assertThat(meterRegistry.get("payment_success").counter().count()).isEqualTo(1);
	}

	@ParameterizedTest
	@MethodSource("failedOutcomes")
	void failedOutcomesLeaveTheOrderRetryableWithoutSellingOrReleasing(
			String token,
			PaymentStatus expectedStatus,
			String expectedFailureReason) {
		stubPendingPayment(expectedStatus, expectedFailureReason);

		PaymentResponse response = paymentService.createPayment(
				ORDER_ID,
				USER_ID,
				new PaymentCreateRequest(token));

		assertThat(response.status()).isEqualTo(expectedStatus);
		assertThat(response.failureReason()).isEqualTo(expectedFailureReason);
		// A decline or provider timeout must not strand the seats the customer still holds:
		// the order stays PENDING so the attempt can be repeated inside the hold window.
		verify(orderMapper, never()).updateStatus(any(), any(), any(), any(), any());
		verify(reservationMapper, never()).updateStatus(any(), any(), any(), any());
		verify(eventSeatMapper, never()).markSold(any());
		verify(ticketService, never()).issueTickets(any(), any(), any());
		verify(outboxService, never()).recordOrderPaid(any(), any(), any(), any());
		verify(eventPublisher, never()).publishEvent(any());
		assertThat(meterRegistry.get("payment_failure").counter().count()).isEqualTo(1);
	}

	@Test
	void conditionalSeatUpdateFailureAbortsPurchase() {
		stubPurchaseData(reservation(ReservationStatus.PENDING_PAYMENT, NOW.plusSeconds(60)));
		when(seatHoldStore.isHoldActive(any(SeatHoldRecord.class))).thenReturn(true);
		when(paymentMapper.insertPending(any(), eq(ORDER_ID), eq(USER_ID), any(), eq(NOW))).thenReturn(1);
		when(orderMapper.updateStatus(ORDER_ID, USER_ID, OrderStatus.PENDING, OrderStatus.PAID, NOW)).thenReturn(1);
		when(reservationMapper.updateStatus(
				RESERVATION_ID,
				ReservationStatus.PENDING_PAYMENT,
				ReservationStatus.CONFIRMED,
				NOW)).thenReturn(1);
		when(eventSeatMapper.markSold(EVENT_SEAT_ID_1)).thenReturn(1);
		when(eventSeatMapper.markSold(EVENT_SEAT_ID_2)).thenReturn(0);

		assertThatThrownBy(() -> paymentService.createPayment(
				ORDER_ID,
				USER_ID,
				new PaymentCreateRequest("tok_success")))
				.isInstanceOf(PaymentConflictException.class);

		verify(paymentMapper, never()).updateStatus(any(), any(), any(), any(), any());
		verify(ticketService, never()).issueTickets(any(), any(), any());
		verify(outboxService, never()).recordOrderPaid(any(), any(), any(), any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void outboxFailureAbortsBeforeHoldReleaseIsPublished() {
		stubPendingPayment(PaymentStatus.SUCCEEDED, null);
		when(reservationMapper.updateStatus(
				RESERVATION_ID,
				ReservationStatus.PENDING_PAYMENT,
				ReservationStatus.CONFIRMED,
				NOW)).thenReturn(1);
		when(eventSeatMapper.markSold(any())).thenReturn(1);
		when(outboxService.recordOrderPaid(any(), any(), any(), eq(NOW)))
				.thenThrow(new OutboxStorageException(new IllegalStateException("outbox unavailable")));

		assertThatThrownBy(() -> paymentService.createPayment(
				ORDER_ID,
				USER_ID,
				new PaymentCreateRequest("tok_success")))
				.isInstanceOf(OutboxStorageException.class);

		verify(ticketService).issueTickets(ORDER_ID, List.of(EVENT_SEAT_ID_1, EVENT_SEAT_ID_2), NOW);
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void expiredDurableHoldIsRejectedAfterSeatsAreLocked() {
		stubPurchaseData(reservation(ReservationStatus.PENDING_PAYMENT, NOW));

		assertThatThrownBy(() -> paymentService.createPayment(
				ORDER_ID,
				USER_ID,
				new PaymentCreateRequest("tok_success")))
				.isInstanceOf(PaymentConflictException.class);

		verify(eventSeatMapper).lockByIds(List.of(EVENT_SEAT_ID_1, EVENT_SEAT_ID_2));
		verify(paymentMapper, never()).insertPending(any(), any(), any(), any(), any());
	}

	@Test
	void inactiveRedisHoldIsRejectedAfterSeatsAreLocked() {
		stubPurchaseData(reservation(ReservationStatus.PENDING_PAYMENT, NOW.plusSeconds(60)));
		when(seatHoldStore.isHoldActive(any(SeatHoldRecord.class))).thenReturn(false);

		assertThatThrownBy(() -> paymentService.createPayment(
				ORDER_ID,
				USER_ID,
				new PaymentCreateRequest("tok_success")))
				.isInstanceOf(PaymentConflictException.class);

		verify(eventSeatMapper).lockByIds(List.of(EVENT_SEAT_ID_1, EVENT_SEAT_ID_2));
		verify(paymentMapper, never()).insertPending(any(), any(), any(), any(), any());
	}

	@Test
	void wrongOwnerCannotCreatePayment() {
		when(orderMapper.findByIdAndUser(ORDER_ID, OTHER_USER_ID)).thenReturn(null);

		assertThatThrownBy(() -> paymentService.createPayment(
				ORDER_ID,
				OTHER_USER_ID,
				new PaymentCreateRequest("tok_success")))
				.isInstanceOf(OrderNotFoundException.class);

		verify(eventSeatMapper, never()).lockByIds(any());
	}

	@Test
	void paidOrderCannotBePaidAgain() {
		when(orderMapper.findByIdAndUser(ORDER_ID, USER_ID)).thenReturn(order(OrderStatus.PAID));

		assertThatThrownBy(() -> paymentService.createPayment(
				ORDER_ID,
				USER_ID,
				new PaymentCreateRequest("tok_success")))
				.isInstanceOf(PaymentConflictException.class);

		verify(eventSeatMapper, never()).lockByIds(any());
	}

	@Test
	void unknownTokenIsRejectedBeforeDatabaseAccess() {
		assertThatThrownBy(() -> paymentService.createPayment(
				ORDER_ID,
				USER_ID,
				new PaymentCreateRequest("not-a-token")))
				.isInstanceOf(InvalidPaymentTokenException.class);

		verify(orderMapper, never()).findByIdAndUser(any(), any());
	}

	private void stubPendingPayment(PaymentStatus terminalStatus, String failureReason) {
		stubPurchaseData(reservation(ReservationStatus.PENDING_PAYMENT, NOW.plusSeconds(60)));
		when(seatHoldStore.isHoldActive(any(SeatHoldRecord.class))).thenReturn(true);
		when(paymentMapper.insertPending(any(), eq(ORDER_ID), eq(USER_ID), any(), eq(NOW))).thenReturn(1);
		if (terminalStatus == PaymentStatus.SUCCEEDED) {
			// Only a successful attempt moves the order off PENDING.
			when(orderMapper.updateStatus(eq(ORDER_ID), eq(USER_ID), eq(OrderStatus.PENDING), any(), eq(NOW)))
					.thenReturn(1);
		}
		when(paymentMapper.updateStatus(any(), eq(PaymentStatus.PENDING), eq(terminalStatus),
				eq(failureReason), eq(NOW))).thenReturn(1);
		when(paymentMapper.findById(any())).thenReturn(payment(terminalStatus, failureReason));
	}

	private void stubPurchaseData(ReservationRecord reservation) {
		when(orderMapper.findByIdAndUser(ORDER_ID, USER_ID)).thenReturn(order(OrderStatus.PENDING));
		when(reservationMapper.findByIdAndUser(RESERVATION_ID, USER_ID)).thenReturn(reservation);
		when(reservationItemMapper.findByReservationId(RESERVATION_ID)).thenReturn(List.of(
				reservationItem(EVENT_SEAT_ID_2),
				reservationItem(EVENT_SEAT_ID_1)));
		when(eventSeatMapper.lockByIds(List.of(EVENT_SEAT_ID_1, EVENT_SEAT_ID_2)))
				.thenReturn(List.of(
						eventSeat(EVENT_SEAT_ID_1, SEAT_ID_1),
						eventSeat(EVENT_SEAT_ID_2, SEAT_ID_2)));
	}

	private static OrderRecord order(OrderStatus status) {
		return new OrderRecord(
				ORDER_ID,
				RESERVATION_ID,
				USER_ID,
				status,
				new BigDecimal("210000.75"),
				"VND",
				NOW.minusSeconds(60),
				NOW.minusSeconds(60));
	}

	private static ReservationRecord reservation(ReservationStatus status, Instant expiresAt) {
		return new ReservationRecord(
				RESERVATION_ID,
				USER_ID,
				EVENT_ID,
				HOLD_ID,
				status,
				expiresAt,
				new BigDecimal("210000.75"),
				NOW.minusSeconds(60),
				NOW.minusSeconds(60));
	}

	private static ReservationItemRecord reservationItem(UUID eventSeatId) {
		return new ReservationItemRecord(
				UUID.randomUUID(),
				RESERVATION_ID,
				eventSeatId,
				new BigDecimal("105000.00"),
				NOW.minusSeconds(60));
	}

	private static EventSeatRecord eventSeat(UUID eventSeatId, UUID seatId) {
		return new EventSeatRecord(
				eventSeatId,
				EVENT_ID,
				seatId,
				new BigDecimal("105000.00"),
				EventSeatStatus.AVAILABLE,
				0,
				NOW.minusSeconds(60),
				NOW.minusSeconds(60));
	}

	private static PaymentRecord payment(PaymentStatus status, String failureReason) {
		return new PaymentRecord(
				PAYMENT_ID,
				ORDER_ID,
				status,
				new BigDecimal("210000.75"),
				"sim_" + PAYMENT_ID,
				failureReason,
				NOW,
				NOW);
	}

	private static java.util.stream.Stream<Arguments> failedOutcomes() {
		return java.util.stream.Stream.of(
				Arguments.of("tok_declined", PaymentStatus.DECLINED, "Payment declined"),
				Arguments.of("tok_timeout", PaymentStatus.TIMED_OUT, "Payment timed out"),
				Arguments.of("tok_error", PaymentStatus.FAILED, "Simulated provider error"));
	}
}
