package com.seatflow.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatflow.event.EventSeatMapper;
import com.seatflow.event.EventSeatRecord;
import com.seatflow.event.EventSeatStatus;
import com.seatflow.hold.SeatHoldRecord;
import com.seatflow.hold.SeatHoldStorageException;
import com.seatflow.hold.SeatHoldStore;
import com.seatflow.order.OrderMapper;
import com.seatflow.order.OrderNotFoundException;
import com.seatflow.order.OrderRecord;
import com.seatflow.order.OrderStatus;
import com.seatflow.reservation.ReservationItemMapper;
import com.seatflow.reservation.ReservationItemRecord;
import com.seatflow.reservation.ReservationMapper;
import com.seatflow.reservation.ReservationRecord;
import com.seatflow.reservation.ReservationStatus;

@Service
public class PaymentService {

	private final PaymentMapper paymentMapper;
	private final OrderMapper orderMapper;
	private final ReservationMapper reservationMapper;
	private final ReservationItemMapper reservationItemMapper;
	private final EventSeatMapper eventSeatMapper;
	private final SeatHoldStore seatHoldStore;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public PaymentService(
			PaymentMapper paymentMapper,
			OrderMapper orderMapper,
			ReservationMapper reservationMapper,
			ReservationItemMapper reservationItemMapper,
			EventSeatMapper eventSeatMapper,
			SeatHoldStore seatHoldStore,
			ApplicationEventPublisher eventPublisher,
			Clock clock) {
		this.paymentMapper = paymentMapper;
		this.orderMapper = orderMapper;
		this.reservationMapper = reservationMapper;
		this.reservationItemMapper = reservationItemMapper;
		this.eventSeatMapper = eventSeatMapper;
		this.seatHoldStore = seatHoldStore;
		this.eventPublisher = eventPublisher;
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

		ReservationRecord reservation = reservationMapper.findByIdAndUser(order.reservationId(), userId);
		if (reservation == null) {
			throw new PaymentConflictException();
		}
		List<ReservationItemRecord> reservationItems = reservationItemMapper.findByReservationId(reservation.id());
		List<UUID> orderedEventSeatIds = orderedEventSeatIds(reservationItems);
		List<EventSeatRecord> lockedSeats = eventSeatMapper.lockByIds(orderedEventSeatIds);
		Instant now = clock.instant();
		SeatHoldRecord hold = validatePurchase(
				reservation,
				userId,
				orderedEventSeatIds,
				lockedSeats,
				now);
		if (!isHoldActive(hold)) {
			throw new PaymentConflictException();
		}

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
			completeSuccessfulPayment(reservation, lockedSeats, now);
		}
		else if (reservationMapper.updateStatus(
					reservation.id(),
					ReservationStatus.PENDING_PAYMENT,
					ReservationStatus.PAYMENT_FAILED,
					now) != 1) {
			throw new PaymentConflictException();
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
		if (outcome.successful()) {
			eventPublisher.publishEvent(new SeatHoldReleaseRequested(hold));
		}
		return PaymentResponse.from(payment);
	}

	private void completeSuccessfulPayment(
			ReservationRecord reservation,
			List<EventSeatRecord> lockedSeats,
			Instant now) {
		if (reservationMapper.updateStatus(
				reservation.id(),
				ReservationStatus.PENDING_PAYMENT,
				ReservationStatus.CONFIRMED,
				now) != 1) {
			throw new PaymentConflictException();
		}

		for (EventSeatRecord lockedSeat : lockedSeats) {
			if (eventSeatMapper.markSold(lockedSeat.id()) != 1) {
				throw new PaymentConflictException();
			}
		}
	}

	private boolean isHoldActive(SeatHoldRecord hold) {
		try {
			return seatHoldStore.isHoldActive(hold);
		}
		catch (RuntimeException ex) {
			throw new SeatHoldStorageException(ex);
		}
	}

	private static List<UUID> orderedEventSeatIds(List<ReservationItemRecord> reservationItems) {
		if (reservationItems.isEmpty()) {
			throw new PaymentConflictException();
		}
		List<UUID> orderedIds = reservationItems.stream()
				.map(ReservationItemRecord::eventSeatId)
				.distinct()
				.sorted(Comparator.comparing(UUID::toString))
				.toList();
		if (orderedIds.size() != reservationItems.size()) {
			throw new PaymentConflictException();
		}
		return orderedIds;
	}

	private static SeatHoldRecord validatePurchase(
			ReservationRecord reservation,
			UUID userId,
			List<UUID> orderedEventSeatIds,
			List<EventSeatRecord> lockedSeats,
			Instant now) {
		if (!reservation.userId().equals(userId)
				|| reservation.status() != ReservationStatus.PENDING_PAYMENT
				|| !reservation.expiresAt().isAfter(now)
				|| lockedSeats.size() != orderedEventSeatIds.size()) {
			throw new PaymentConflictException();
		}

		for (int index = 0; index < lockedSeats.size(); index++) {
			EventSeatRecord lockedSeat = lockedSeats.get(index);
			if (!lockedSeat.id().equals(orderedEventSeatIds.get(index))
					|| !lockedSeat.eventId().equals(reservation.eventId())
					|| lockedSeat.permanentStatus() != EventSeatStatus.AVAILABLE) {
				throw new PaymentConflictException();
			}
		}

		return new SeatHoldRecord(
				reservation.holdId(),
				reservation.eventId(),
				lockedSeats.stream().map(EventSeatRecord::id).toList(),
				lockedSeats.stream().map(EventSeatRecord::seatId).toList(),
				reservation.userId(),
				reservation.expiresAt());
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
