package com.seatflow.order;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatflow.observability.BusinessMetrics;
import com.seatflow.reservation.ReservationMapper;
import com.seatflow.reservation.ReservationRecord;
import com.seatflow.reservation.ReservationStatus;

/**
 * Lets a buyer walk away from an order they have not paid for. Paid orders are not cancellable
 * here: giving money back is a staff decision and goes through the refund flow.
 */
@Service
public class OrderCancellationService {

	private final OrderMapper orderMapper;
	private final ReservationMapper reservationMapper;
	private final ApplicationEventPublisher eventPublisher;
	private final BusinessMetrics businessMetrics;
	private final Clock clock;

	public OrderCancellationService(
			OrderMapper orderMapper,
			ReservationMapper reservationMapper,
			ApplicationEventPublisher eventPublisher,
			BusinessMetrics businessMetrics,
			Clock clock) {
		this.orderMapper = orderMapper;
		this.reservationMapper = reservationMapper;
		this.eventPublisher = eventPublisher;
		this.businessMetrics = businessMetrics;
		this.clock = clock;
	}

	@Transactional
	public OrderResponse cancelOrder(UUID orderId, UUID userId) {
		// Locking the row first makes a cancel and a payment on the same order take turns: whichever
		// loses re-reads the status afterwards and backs off instead of overwriting the winner.
		OrderRecord order = orderMapper.findByIdForUpdate(orderId);
		if (order == null || !order.userId().equals(userId)) {
			throw new OrderNotFoundException();
		}
		if (order.status() == OrderStatus.CANCELLED) {
			return OrderResponse.from(order);
		}
		if (order.status() != OrderStatus.PENDING) {
			throw new OrderConflictException();
		}

		Instant now = clock.instant();
		if (orderMapper.transition(orderId, OrderStatus.PENDING, OrderStatus.CANCELLED, now) != 1) {
			throw new OrderConflictException();
		}

		// The reservation may already have been closed by the expiry sweep; the order is cancelled
		// either way, so a reservation that is no longer pending is not an error.
		ReservationRecord reservation = reservationMapper.findByIdAndUser(order.reservationId(), userId);
		if (reservation != null) {
			reservationMapper.updateStatus(
					reservation.id(), ReservationStatus.PENDING_PAYMENT, ReservationStatus.CANCELLED, now);
			eventPublisher.publishEvent(new OrderCancelled(reservation.holdId(), userId));
		}
		businessMetrics.orderCancelled();
		return OrderResponse.from(orderMapper.findByIdAndUser(orderId, userId));
	}
}
