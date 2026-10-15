package com.seatflow.refund;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.seatflow.event.EventSeatMapper;
import com.seatflow.event.EventSeatRecord;
import com.seatflow.event.EventSeatStatus;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.order.OrderMapper;
import com.seatflow.order.OrderNotFoundException;
import com.seatflow.order.OrderRecord;
import com.seatflow.order.OrderStatus;
import com.seatflow.outbox.OutboxService;
import com.seatflow.payment.PaymentMapper;
import com.seatflow.payment.PaymentRecord;
import com.seatflow.payment.PaymentStatus;
import com.seatflow.reservation.ReservationItemMapper;
import com.seatflow.reservation.ReservationItemRecord;
import com.seatflow.reservation.ReservationMapper;
import com.seatflow.reservation.ReservationRecord;
import com.seatflow.reservation.ReservationStatus;
import com.seatflow.ticket.TicketMapper;
import com.seatflow.ticket.TicketRecord;
import com.seatflow.ticket.TicketStatus;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class RefundServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
	private static final UUID ORDER_ID = UUID.fromString("e969ef46-1ed2-488f-8723-4833a1c0abca");
	private static final UUID USER_ID = UUID.fromString("0a8b70f9-cbe7-4a57-bd34-b4a2f133779e");
	private static final UUID ADMIN_ID = UUID.fromString("7ca3110b-513f-4445-a52d-dc96b1c2da8f");
	private static final UUID RESERVATION_ID = UUID.fromString("4c26148f-0a53-4f64-b115-72dfbb1849d4");
	private static final UUID EVENT_ID = UUID.fromString("9a2c4f10-6a1e-4c55-b3d4-1d0f6a8e7b21");
	private static final UUID PAYMENT_ID = UUID.fromString("3f1d6a52-27b4-4a9e-8f0c-5c7e2d1b9a10");
	private static final UUID SEAT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

	@Mock
	private OrderMapper orderMapper;

	@Mock
	private PaymentMapper paymentMapper;

	@Mock
	private TicketMapper ticketMapper;

	@Mock
	private ReservationMapper reservationMapper;

	@Mock
	private ReservationItemMapper reservationItemMapper;

	@Mock
	private EventSeatMapper eventSeatMapper;

	@Mock
	private RefundMapper refundMapper;

	@Mock
	private OutboxService outboxService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private RefundService service;

	@BeforeEach
	void setUp() {
		service = new RefundService(
				orderMapper, paymentMapper, ticketMapper, reservationMapper, reservationItemMapper,
				eventSeatMapper, refundMapper, outboxService, eventPublisher,
				new BusinessMetrics(new SimpleMeterRegistry()), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void unknownOrderIsNotFound() {
		assertThatThrownBy(() -> service.refundOrder(ORDER_ID, ADMIN_ID, RefundRequest.NONE))
				.isInstanceOf(OrderNotFoundException.class);
	}

	@Test
	void ordersThatWereNeverPaidAreRejectedWithoutTouchingAnything() {
		for (OrderStatus status : List.of(OrderStatus.PENDING, OrderStatus.FAILED, OrderStatus.CANCELLED)) {
			when(orderMapper.findByIdForUpdate(ORDER_ID)).thenReturn(order(status));

			assertThatThrownBy(() -> service.refundOrder(ORDER_ID, ADMIN_ID, RefundRequest.NONE))
					.isInstanceOf(OrderNotRefundableException.class);
		}

		verify(orderMapper, never()).transition(any(), any(), any(), any());
		verify(refundMapper, never()).insert(any());
	}

	@Test
	void alreadyRefundedOrderReturnsTheRecordedRefund() {
		RefundRecord recorded = refund();
		when(orderMapper.findByIdForUpdate(ORDER_ID)).thenReturn(order(OrderStatus.REFUNDED));
		when(refundMapper.findByOrderId(ORDER_ID)).thenReturn(recorded);

		RefundResponse response = service.refundOrder(ORDER_ID, ADMIN_ID, new RefundRequest("again"));

		assertThat(response.id()).isEqualTo(recorded.id());
		assertThat(response.orderStatus()).isEqualTo(OrderStatus.REFUNDED);
		verify(orderMapper, never()).transition(any(), any(), any(), any());
		verify(refundMapper, never()).insert(any());
	}

	@Test
	void refundedOrderWithoutARecordIsReportedNotInventedAgain() {
		when(orderMapper.findByIdForUpdate(ORDER_ID)).thenReturn(order(OrderStatus.REFUNDED));

		assertThatThrownBy(() -> service.refundOrder(ORDER_ID, ADMIN_ID, RefundRequest.NONE))
				.isInstanceOf(OrderNotRefundableException.class);
	}

	@Test
	void missingSettledPaymentBlocksTheRefund() {
		givenPaidOrder();
		when(paymentMapper.findSucceededByOrderId(ORDER_ID)).thenReturn(null);

		assertThatThrownBy(() -> service.refundOrder(ORDER_ID, ADMIN_ID, RefundRequest.NONE))
				.isInstanceOf(OrderNotRefundableException.class);

		verify(orderMapper, never()).transition(any(), any(), any(), any());
	}

	@Test
	void usedTicketBlocksTheRefund() {
		givenPaidOrder();
		givenSettledPayment();
		when(ticketMapper.findByOrderId(ORDER_ID)).thenReturn(List.of(ticket(TicketStatus.USED)));

		assertThatThrownBy(() -> service.refundOrder(ORDER_ID, ADMIN_ID, RefundRequest.NONE))
				.isInstanceOf(OrderNotRefundableException.class)
				.hasMessageContaining("used");

		verify(orderMapper, never()).transition(any(), any(), any(), any());
	}

	@Test
	void seatsThatAreNotSoldBlockTheRefund() {
		givenPaidOrder();
		givenSettledPayment();
		when(ticketMapper.findByOrderId(ORDER_ID)).thenReturn(List.of(ticket(TicketStatus.ACTIVE)));
		when(reservationItemMapper.findByReservationId(RESERVATION_ID)).thenReturn(List.of(item()));
		when(eventSeatMapper.lockByIds(List.of(SEAT_ID))).thenReturn(List.of(seat(EventSeatStatus.AVAILABLE)));

		assertThatThrownBy(() -> service.refundOrder(ORDER_ID, ADMIN_ID, RefundRequest.NONE))
				.isInstanceOf(OrderNotRefundableException.class);

		verify(orderMapper, never()).transition(any(), any(), any(), any());
	}

	@Test
	void everyStepMustTakeEffectSoAConcurrentChangeAbortsTheRefund() {
		givenPaidOrder();
		givenSettledPayment();
		when(ticketMapper.findByOrderId(ORDER_ID)).thenReturn(List.of(ticket(TicketStatus.ACTIVE)));
		when(reservationItemMapper.findByReservationId(RESERVATION_ID)).thenReturn(List.of(item()));
		when(eventSeatMapper.lockByIds(List.of(SEAT_ID))).thenReturn(List.of(seat(EventSeatStatus.SOLD)));
		when(orderMapper.transition(eq(ORDER_ID), eq(OrderStatus.PAID), eq(OrderStatus.REFUNDED), any())).thenReturn(0);

		assertThatThrownBy(() -> service.refundOrder(ORDER_ID, ADMIN_ID, RefundRequest.NONE))
				.isInstanceOf(OrderNotRefundableException.class);

		verify(refundMapper, never()).insert(any());
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void successfulRefundSettlesEverythingAndAnnouncesIt() {
		givenPaidOrder();
		givenSettledPayment();
		when(ticketMapper.findByOrderId(ORDER_ID)).thenReturn(List.of(ticket(TicketStatus.ACTIVE)));
		when(reservationItemMapper.findByReservationId(RESERVATION_ID)).thenReturn(List.of(item()));
		when(eventSeatMapper.lockByIds(List.of(SEAT_ID))).thenReturn(List.of(seat(EventSeatStatus.SOLD)));
		when(orderMapper.transition(ORDER_ID, OrderStatus.PAID, OrderStatus.REFUNDED, NOW)).thenReturn(1);
		when(paymentMapper.updateStatus(PAYMENT_ID, PaymentStatus.SUCCEEDED, PaymentStatus.REFUNDED, null, NOW))
				.thenReturn(1);
		when(ticketMapper.cancelActiveByOrderId(ORDER_ID)).thenReturn(1);
		when(eventSeatMapper.markAvailable(SEAT_ID)).thenReturn(1);
		when(reservationMapper.updateStatus(
				RESERVATION_ID, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, NOW)).thenReturn(1);
		when(refundMapper.insert(any())).thenReturn(1);

		RefundResponse response = service.refundOrder(ORDER_ID, ADMIN_ID, new RefundRequest("  oops "));

		assertThat(response.orderStatus()).isEqualTo(OrderStatus.REFUNDED);
		assertThat(response.amount()).isEqualByComparingTo("210000");
		assertThat(response.reason()).isEqualTo("oops");
		verify(outboxService).recordOrderRefunded(any(), any(), eq(List.of(SEAT_ID)));
		verify(eventPublisher).publishEvent(new SeatsRestocked(EVENT_ID, List.of(SEAT_ID)));
	}

	@Test
	void blankReasonIsStoredAsNoReason() {
		assertThat(new RefundRequest("   ").normalizedReason()).isNull();
		assertThat(RefundRequest.NONE.normalizedReason()).isNull();
		assertThat(new RefundRequest(" duplicate ").normalizedReason()).isEqualTo("duplicate");
	}

	private void givenPaidOrder() {
		when(orderMapper.findByIdForUpdate(ORDER_ID)).thenReturn(order(OrderStatus.PAID));
		when(reservationMapper.findByIdAndUser(RESERVATION_ID, USER_ID)).thenReturn(reservation());
	}

	private void givenSettledPayment() {
		when(paymentMapper.findSucceededByOrderId(ORDER_ID)).thenReturn(new PaymentRecord(
				PAYMENT_ID, ORDER_ID, PaymentStatus.SUCCEEDED, new BigDecimal("210000.00"), "sim_ref", null, NOW, NOW));
	}

	private static OrderRecord order(OrderStatus status) {
		return new OrderRecord(
				ORDER_ID, RESERVATION_ID, USER_ID, status, new BigDecimal("210000.00"), "VND", NOW, NOW);
	}

	private static ReservationRecord reservation() {
		return new ReservationRecord(
				RESERVATION_ID, USER_ID, EVENT_ID, UUID.randomUUID(), ReservationStatus.CONFIRMED,
				NOW.plusSeconds(60), new BigDecimal("210000.00"), NOW, NOW);
	}

	private static RefundRecord refund() {
		return new RefundRecord(
				UUID.randomUUID(), ORDER_ID, PAYMENT_ID, new BigDecimal("210000.00"), "VND", "first", ADMIN_ID, NOW);
	}

	private static TicketRecord ticket(TicketStatus status) {
		return new TicketRecord(UUID.randomUUID(), ORDER_ID, SEAT_ID, "code", status, NOW, null, NOW);
	}

	private static ReservationItemRecord item() {
		return new ReservationItemRecord(UUID.randomUUID(), RESERVATION_ID, SEAT_ID, new BigDecimal("210000.00"), NOW);
	}

	private static EventSeatRecord seat(EventSeatStatus status) {
		return new EventSeatRecord(SEAT_ID, EVENT_ID, UUID.randomUUID(), new BigDecimal("210000.00"), status, 0, NOW, NOW);
	}
}
