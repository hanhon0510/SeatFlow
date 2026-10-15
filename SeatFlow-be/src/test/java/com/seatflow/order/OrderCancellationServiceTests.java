package com.seatflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.seatflow.observability.BusinessMetrics;
import com.seatflow.reservation.ReservationMapper;
import com.seatflow.reservation.ReservationRecord;
import com.seatflow.reservation.ReservationStatus;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
	private static final UUID USER_ID = UUID.fromString("0a8b70f9-cbe7-4a57-bd34-b4a2f133779e");
	private static final UUID OTHER_USER_ID = UUID.fromString("7ca3110b-513f-4445-a52d-dc96b1c2da8f");
	private static final UUID ORDER_ID = UUID.fromString("e969ef46-1ed2-488f-8723-4833a1c0abca");
	private static final UUID RESERVATION_ID = UUID.fromString("4c26148f-0a53-4f64-b115-72dfbb1849d4");
	private static final UUID HOLD_ID = UUID.fromString("3f1d6a52-27b4-4a9e-8f0c-5c7e2d1b9a10");

	@Mock
	private OrderMapper orderMapper;

	@Mock
	private ReservationMapper reservationMapper;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private OrderCancellationService service;

	@BeforeEach
	void setUp() {
		service = new OrderCancellationService(
				orderMapper, reservationMapper, eventPublisher,
				new BusinessMetrics(new SimpleMeterRegistry()), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void pendingOrderIsCancelledTogetherWithItsReservationAndItsHoldIsReleased() {
		when(orderMapper.findByIdForUpdate(ORDER_ID)).thenReturn(order(OrderStatus.PENDING, USER_ID));
		when(orderMapper.transition(ORDER_ID, OrderStatus.PENDING, OrderStatus.CANCELLED, NOW)).thenReturn(1);
		when(reservationMapper.findByIdAndUser(RESERVATION_ID, USER_ID)).thenReturn(reservation());
		when(orderMapper.findByIdAndUser(ORDER_ID, USER_ID)).thenReturn(order(OrderStatus.CANCELLED, USER_ID));

		OrderResponse response = service.cancelOrder(ORDER_ID, USER_ID);

		assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
		verify(reservationMapper).updateStatus(
				RESERVATION_ID, ReservationStatus.PENDING_PAYMENT, ReservationStatus.CANCELLED, NOW);
		verify(eventPublisher).publishEvent(new OrderCancelled(HOLD_ID, USER_ID));
	}

	@Test
	void cancellingTwiceIsANoOp() {
		when(orderMapper.findByIdForUpdate(ORDER_ID)).thenReturn(order(OrderStatus.CANCELLED, USER_ID));

		assertThat(service.cancelOrder(ORDER_ID, USER_ID).status()).isEqualTo(OrderStatus.CANCELLED);

		verify(orderMapper, never()).transition(any(), any(), any(), any());
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void anotherUsersOrderLooksNonexistent() {
		when(orderMapper.findByIdForUpdate(ORDER_ID)).thenReturn(order(OrderStatus.PENDING, OTHER_USER_ID));

		assertThatThrownBy(() -> service.cancelOrder(ORDER_ID, USER_ID))
				.isInstanceOf(OrderNotFoundException.class);
		assertThatThrownBy(() -> {
			when(orderMapper.findByIdForUpdate(ORDER_ID)).thenReturn(null);
			service.cancelOrder(ORDER_ID, USER_ID);
		}).isInstanceOf(OrderNotFoundException.class);

		verify(orderMapper, never()).transition(any(), any(), any(), any());
	}

	@Test
	void paidFailedAndRefundedOrdersCannotBeCancelled() {
		for (OrderStatus status : new OrderStatus[] {OrderStatus.PAID, OrderStatus.FAILED, OrderStatus.REFUNDED}) {
			when(orderMapper.findByIdForUpdate(ORDER_ID)).thenReturn(order(status, USER_ID));

			assertThatThrownBy(() -> service.cancelOrder(ORDER_ID, USER_ID))
					.isInstanceOf(OrderConflictException.class);
		}

		verify(orderMapper, never()).transition(any(), any(), any(), any());
	}

	@Test
	void lostCompareAndSetIsAConflict() {
		when(orderMapper.findByIdForUpdate(ORDER_ID)).thenReturn(order(OrderStatus.PENDING, USER_ID));
		when(orderMapper.transition(ORDER_ID, OrderStatus.PENDING, OrderStatus.CANCELLED, NOW)).thenReturn(0);

		assertThatThrownBy(() -> service.cancelOrder(ORDER_ID, USER_ID))
				.isInstanceOf(OrderConflictException.class);

		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	private static OrderRecord order(OrderStatus status, UUID userId) {
		return new OrderRecord(
				ORDER_ID, RESERVATION_ID, userId, status, new BigDecimal("210000.00"), "VND", NOW, NOW);
	}

	private static ReservationRecord reservation() {
		return new ReservationRecord(
				RESERVATION_ID, USER_ID, UUID.randomUUID(), HOLD_ID, ReservationStatus.PENDING_PAYMENT,
				NOW.plusSeconds(60), new BigDecimal("210000.00"), NOW, NOW);
	}
}
