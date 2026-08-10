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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.seatflow.order.OrderMapper;
import com.seatflow.order.OrderNotFoundException;
import com.seatflow.order.OrderRecord;
import com.seatflow.order.OrderStatus;
import com.seatflow.reservation.ReservationMapper;
import com.seatflow.reservation.ReservationStatus;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
	private static final UUID USER_ID = UUID.fromString("0abcc6a7-eb30-46aa-be8b-677811650fe4");
	private static final UUID OTHER_USER_ID = UUID.fromString("c5405e14-0d4b-4db0-b598-65698b8d6748");
	private static final UUID ORDER_ID = UUID.fromString("2ae13b6b-df9b-4e08-99ae-f6b13f1997b0");
	private static final UUID RESERVATION_ID = UUID.fromString("db84eccc-adb4-499a-aef5-8090035ca183");
	private static final UUID PAYMENT_ID = UUID.fromString("fd9e706d-a502-41ae-9012-a20e24625333");

	@Mock
	private PaymentMapper paymentMapper;

	@Mock
	private OrderMapper orderMapper;

	@Mock
	private ReservationMapper reservationMapper;

	private PaymentService paymentService;

	@BeforeEach
	void setUp() {
		paymentService = new PaymentService(
				paymentMapper,
				orderMapper,
				reservationMapper,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void successPaysOrderConfirmsReservationAndSellsSeats() {
		stubPendingPayment(PaymentStatus.SUCCEEDED, null);
		when(reservationMapper.updateStatus(
				RESERVATION_ID,
				ReservationStatus.PENDING_PAYMENT,
				ReservationStatus.CONFIRMED,
				NOW)).thenReturn(1);
		when(paymentMapper.countReservationItems(ORDER_ID)).thenReturn(2L);
		when(paymentMapper.sellReservationSeats(ORDER_ID, NOW)).thenReturn(2);

		PaymentResponse response = paymentService.createPayment(
				ORDER_ID,
				USER_ID,
				new PaymentCreateRequest("tok_success"));

		assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
		assertThat(response.amount()).isEqualByComparingTo("210000.75");
		assertThat(response.failureReason()).isNull();
		verify(orderMapper).updateStatus(ORDER_ID, USER_ID, OrderStatus.PENDING, OrderStatus.PAID, NOW);
		verify(paymentMapper).sellReservationSeats(ORDER_ID, NOW);
	}

	@ParameterizedTest
	@MethodSource("failedOutcomes")
	void failedOutcomesFailOrderWithoutSellingSeats(
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
		verify(orderMapper).updateStatus(ORDER_ID, USER_ID, OrderStatus.PENDING, OrderStatus.FAILED, NOW);
		verify(reservationMapper).updateStatus(
				RESERVATION_ID,
				ReservationStatus.PENDING_PAYMENT,
				ReservationStatus.PAYMENT_FAILED,
				NOW);
		verify(paymentMapper, never()).countReservationItems(any());
		verify(paymentMapper, never()).sellReservationSeats(any(), any());
	}

	@Test
	void wrongOwnerCannotCreatePayment() {
		when(orderMapper.findByIdAndUser(ORDER_ID, OTHER_USER_ID)).thenReturn(null);

		assertThatThrownBy(() -> paymentService.createPayment(
				ORDER_ID,
				OTHER_USER_ID,
				new PaymentCreateRequest("tok_success")))
				.isInstanceOf(OrderNotFoundException.class);

		verify(paymentMapper, never()).insertPending(any(), any(), any(), any(), any());
	}

	@Test
	void paidOrderCannotBePaidAgain() {
		when(orderMapper.findByIdAndUser(ORDER_ID, USER_ID)).thenReturn(order(OrderStatus.PAID));

		assertThatThrownBy(() -> paymentService.createPayment(
				ORDER_ID,
				USER_ID,
				new PaymentCreateRequest("tok_success")))
				.isInstanceOf(PaymentConflictException.class);

		verify(paymentMapper, never()).insertPending(any(), any(), any(), any(), any());
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
		when(orderMapper.findByIdAndUser(ORDER_ID, USER_ID)).thenReturn(order(OrderStatus.PENDING));
		when(paymentMapper.insertPending(any(), eq(ORDER_ID), eq(USER_ID), any(), eq(NOW))).thenReturn(1);
		when(orderMapper.updateStatus(eq(ORDER_ID), eq(USER_ID), eq(OrderStatus.PENDING), any(), eq(NOW)))
				.thenReturn(1);
		when(paymentMapper.updateStatus(any(), eq(PaymentStatus.PENDING), eq(terminalStatus),
				eq(failureReason), eq(NOW))).thenReturn(1);
		when(paymentMapper.findById(any())).thenReturn(payment(terminalStatus, failureReason));
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
