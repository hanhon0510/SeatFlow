package com.seatflow.order;

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
import org.springframework.security.access.AccessDeniedException;

import com.seatflow.reservation.ReservationMapper;
import com.seatflow.reservation.ReservationNotFoundException;
import com.seatflow.reservation.ReservationRecord;
import com.seatflow.reservation.ReservationStatus;

@ExtendWith(MockitoExtension.class)
class OrderServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
	private static final UUID USER_ID = UUID.fromString("0a8b70f9-cbe7-4a57-bd34-b4a2f133779e");
	private static final UUID OTHER_USER_ID = UUID.fromString("7ca3110b-513f-4445-a52d-dc96b1c2da8f");
	private static final UUID RESERVATION_ID = UUID.fromString("4c26148f-0a53-4f64-b115-72dfbb1849d4");
	private static final UUID ORDER_ID = UUID.fromString("e969ef46-1ed2-488f-8723-4833a1c0abca");

	@Mock
	private OrderMapper orderMapper;

	@Mock
	private ReservationMapper reservationMapper;

	private OrderService orderService;

	@BeforeEach
	void setUp() {
		orderService = new OrderService(
				orderMapper,
				reservationMapper,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void pendingReservationCreatesVndOrder() {
		ReservationRecord reservation = reservation(ReservationStatus.PENDING_PAYMENT, NOW.plusSeconds(300));
		OrderRecord created = order(USER_ID, NOW);
		when(orderMapper.findActiveByReservationAndUser(RESERVATION_ID, USER_ID))
				.thenReturn(null, created);
		when(reservationMapper.findByIdAndUser(RESERVATION_ID, USER_ID)).thenReturn(reservation);
		when(orderMapper.insertPending(any(), eq(RESERVATION_ID), eq(USER_ID), eq("VND"), eq(NOW)))
				.thenReturn(1);

		OrderResponse response = orderService.createOrder(USER_ID, new OrderCreateRequest(RESERVATION_ID));

		assertThat(response.id()).isEqualTo(ORDER_ID);
		assertThat(response.reservationId()).isEqualTo(RESERVATION_ID);
		assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
		assertThat(response.totalAmount()).isEqualByComparingTo("210000.75");
		assertThat(response.currency()).isEqualTo("VND");
	}

	@Test
	void duplicateRequestReturnsExistingOrderWithoutRevalidatingReservation() {
		OrderRecord existing = order(USER_ID, NOW.minusSeconds(10));
		when(orderMapper.findActiveByReservationAndUser(RESERVATION_ID, USER_ID)).thenReturn(existing);

		OrderResponse response = orderService.createOrder(USER_ID, new OrderCreateRequest(RESERVATION_ID));

		assertThat(response.id()).isEqualTo(existing.id());
		verify(reservationMapper, never()).findByIdAndUser(any(), any());
		verify(orderMapper, never()).insertPending(any(), any(), any(), any(), any());
	}

	@Test
	void foreignOrMissingReservationIsRejected() {
		assertThatThrownBy(() -> orderService.createOrder(
				OTHER_USER_ID,
				new OrderCreateRequest(RESERVATION_ID)))
				.isInstanceOf(ReservationNotFoundException.class);

		verify(orderMapper, never()).insertPending(any(), any(), any(), any(), any());
	}

	@Test
	void expiredOrNonPendingReservationIsRejected() {
		when(reservationMapper.findByIdAndUser(RESERVATION_ID, USER_ID))
				.thenReturn(reservation(ReservationStatus.PENDING_PAYMENT, NOW));

		assertThatThrownBy(() -> orderService.createOrder(USER_ID, new OrderCreateRequest(RESERVATION_ID)))
				.isInstanceOf(OrderConflictException.class);

		when(reservationMapper.findByIdAndUser(RESERVATION_ID, USER_ID))
				.thenReturn(reservation(ReservationStatus.CONFIRMED, NOW.plusSeconds(300)));

		assertThatThrownBy(() -> orderService.createOrder(USER_ID, new OrderCreateRequest(RESERVATION_ID)))
				.isInstanceOf(OrderConflictException.class);
		verify(orderMapper, never()).insertPending(any(), any(), any(), any(), any());
	}

	@Test
	void foreignOrderIsNotReturnedAndOwnerMismatchIsDefensivelyRejected() {
		when(orderMapper.findByIdAndUser(ORDER_ID, OTHER_USER_ID)).thenReturn(null);
		assertThatThrownBy(() -> orderService.getOrder(ORDER_ID, OTHER_USER_ID))
				.isInstanceOf(OrderNotFoundException.class);

		when(orderMapper.findByIdAndUser(ORDER_ID, USER_ID)).thenReturn(order(OTHER_USER_ID, NOW));
		assertThatThrownBy(() -> orderService.getOrder(ORDER_ID, USER_ID))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void userHistoryIsPaginatedAndInvalidBoundsAreRejected() {
		OrderRecord order = order(USER_ID, NOW);
		when(orderMapper.countByUser(USER_ID)).thenReturn(3L);
		when(orderMapper.findPageByUser(USER_ID, 2, 2L)).thenReturn(List.of(order));

		OrderPageResponse response = orderService.listUserOrders(USER_ID, 1, 2);

		assertThat(response.items()).extracting(OrderResponse::id).containsExactly(ORDER_ID);
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.size()).isEqualTo(2);
		assertThat(response.totalItems()).isEqualTo(3);
		assertThat(response.totalPages()).isEqualTo(2);

		assertThatThrownBy(() -> orderService.listUserOrders(USER_ID, -1, 20))
				.isInstanceOf(InvalidOrderPaginationException.class);
		assertThatThrownBy(() -> orderService.listUserOrders(USER_ID, 0, 101))
				.isInstanceOf(InvalidOrderPaginationException.class);
	}

	private static ReservationRecord reservation(ReservationStatus status, Instant expiresAt) {
		return new ReservationRecord(
				RESERVATION_ID,
				USER_ID,
				UUID.randomUUID(),
				UUID.randomUUID(),
				status,
				expiresAt,
				new BigDecimal("1.00"),
				NOW.minusSeconds(60),
				NOW.minusSeconds(60));
	}

	private static OrderRecord order(UUID userId, Instant createdAt) {
		return new OrderRecord(
				ORDER_ID,
				RESERVATION_ID,
				userId,
				OrderStatus.PENDING,
				new BigDecimal("210000.75"),
				"VND",
				createdAt,
				createdAt);
	}
}
