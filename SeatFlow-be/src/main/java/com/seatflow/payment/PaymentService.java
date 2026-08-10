package com.seatflow.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatflow.order.OrderMapper;
import com.seatflow.order.OrderNotFoundException;
import com.seatflow.order.OrderRecord;
import com.seatflow.order.OrderStatus;
import com.seatflow.reservation.ReservationMapper;
import com.seatflow.reservation.ReservationStatus;

@Service
public class PaymentService {

	private final PaymentMapper paymentMapper;
	private final OrderMapper orderMapper;
	private final ReservationMapper reservationMapper;
	private final Clock clock;

	public PaymentService(
			PaymentMapper paymentMapper,
			OrderMapper orderMapper,
			ReservationMapper reservationMapper,
			Clock clock) {
		this.paymentMapper = paymentMapper;
		this.orderMapper = orderMapper;
		this.reservationMapper = reservationMapper;
		this.clock = clock;
	}

	@Transactional
	public PaymentResponse createPayment(UUID orderId, UUID userId, PaymentCreateRequest request) {
		SimulationOutcome outcome = SimulationOutcome.fromToken(request.token());
		OrderRecord order = orderMapper.findByIdAndUser(orderId, userId);
		if (order == null) {
			throw new OrderNotFoundException();
		}
		if (order.status() != OrderStatus.PENDING) {
			throw new PaymentConflictException();
		}

		Instant now = clock.instant();
		UUID paymentId = UUID.randomUUID();
		String providerReference = "sim_" + paymentId;
		if (paymentMapper.insertPending(paymentId, orderId, userId, providerReference, now) != 1) {
			throw new PaymentConflictException();
		}

		OrderStatus targetOrderStatus = outcome.successful() ? OrderStatus.PAID : OrderStatus.FAILED;
		if (orderMapper.updateStatus(order.id(), userId, OrderStatus.PENDING, targetOrderStatus, now) != 1) {
			throw new PaymentConflictException();
		}

		if (outcome.successful()) {
			completeSuccessfulPayment(order, now);
		}
		else {
			reservationMapper.updateStatus(
					order.reservationId(),
					ReservationStatus.PENDING_PAYMENT,
					ReservationStatus.PAYMENT_FAILED,
					now);
		}

		if (paymentMapper.updateStatus(
				paymentId,
				PaymentStatus.PENDING,
				outcome.status(),
				outcome.failureReason(),
				now) != 1) {
			throw new PaymentConflictException();
		}

		PaymentRecord payment = paymentMapper.findById(paymentId);
		if (payment == null) {
			throw new PaymentConflictException();
		}
		return PaymentResponse.from(payment);
	}

	private void completeSuccessfulPayment(OrderRecord order, Instant now) {
		if (reservationMapper.updateStatus(
				order.reservationId(),
				ReservationStatus.PENDING_PAYMENT,
				ReservationStatus.CONFIRMED,
				now) != 1) {
			throw new PaymentConflictException();
		}

		long itemCount = paymentMapper.countReservationItems(order.id());
		if (itemCount < 1 || paymentMapper.sellReservationSeats(order.id(), now) != itemCount) {
			throw new PaymentConflictException();
		}
	}

	private enum SimulationOutcome {

		SUCCESS(PaymentStatus.SUCCEEDED, null),
		DECLINED(PaymentStatus.DECLINED, "Payment declined"),
		TIMEOUT(PaymentStatus.TIMED_OUT, "Payment timed out"),
		ERROR(PaymentStatus.FAILED, "Simulated provider error");

		private final PaymentStatus status;
		private final String failureReason;

		SimulationOutcome(PaymentStatus status, String failureReason) {
			this.status = status;
			this.failureReason = failureReason;
		}

		private PaymentStatus status() {
			return status;
		}

		private String failureReason() {
			return failureReason;
		}

		private boolean successful() {
			return status == PaymentStatus.SUCCEEDED;
		}

		private static SimulationOutcome fromToken(String token) {
			return switch (token == null ? "" : token) {
				case "tok_success" -> SUCCESS;
				case "tok_declined" -> DECLINED;
				case "tok_timeout" -> TIMEOUT;
				case "tok_error" -> ERROR;
				default -> throw new InvalidPaymentTokenException();
			};
		}
	}
}
