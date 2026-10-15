package com.seatflow.refund;

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

/**
 * Reverses a paid order in one transaction: the money, the tickets, the seats and the order itself
 * move together or not at all. A refund is always for the full amount charged.
 */
@Service
public class RefundService {

	private final OrderMapper orderMapper;
	private final PaymentMapper paymentMapper;
	private final TicketMapper ticketMapper;
	private final ReservationMapper reservationMapper;
	private final ReservationItemMapper reservationItemMapper;
	private final EventSeatMapper eventSeatMapper;
	private final RefundMapper refundMapper;
	private final OutboxService outboxService;
	private final ApplicationEventPublisher eventPublisher;
	private final BusinessMetrics businessMetrics;
	private final Clock clock;

	public RefundService(
			OrderMapper orderMapper,
			PaymentMapper paymentMapper,
			TicketMapper ticketMapper,
			ReservationMapper reservationMapper,
			ReservationItemMapper reservationItemMapper,
			EventSeatMapper eventSeatMapper,
			RefundMapper refundMapper,
			OutboxService outboxService,
			ApplicationEventPublisher eventPublisher,
			BusinessMetrics businessMetrics,
			Clock clock) {
		this.orderMapper = orderMapper;
		this.paymentMapper = paymentMapper;
		this.ticketMapper = ticketMapper;
		this.reservationMapper = reservationMapper;
		this.reservationItemMapper = reservationItemMapper;
		this.eventSeatMapper = eventSeatMapper;
		this.refundMapper = refundMapper;
		this.outboxService = outboxService;
		this.eventPublisher = eventPublisher;
		this.businessMetrics = businessMetrics;
		this.clock = clock;
	}

	@Transactional
	public RefundResponse refundOrder(UUID orderId, UUID adminId, RefundRequest request) {
		// The order row lock serialises concurrent refunds of the same order: the loser waits, then
		// finds the order already REFUNDED and gets the first refund back instead of paying out twice.
		OrderRecord order = orderMapper.findByIdForUpdate(orderId);
		if (order == null) {
			throw new OrderNotFoundException();
		}
		if (order.status() == OrderStatus.REFUNDED) {
			return RefundResponse.from(existingRefund(orderId));
		}
		if (order.status() != OrderStatus.PAID) {
			throw new OrderNotRefundableException("Only paid orders can be refunded");
		}

		ReservationRecord reservation = reservationMapper.findByIdAndUser(order.reservationId(), order.userId());
		PaymentRecord payment = paymentMapper.findSucceededByOrderId(orderId);
		if (reservation == null || payment == null) {
			throw new OrderNotRefundableException("The order has no settled payment to refund");
		}
		List<TicketRecord> tickets = ticketMapper.findByOrderId(orderId);
		if (tickets.stream().anyMatch(ticket -> ticket.status() == TicketStatus.USED)) {
			throw new OrderNotRefundableException("Tickets from this order have already been used");
		}

		List<EventSeatRecord> seats = lockSoldSeats(reservation);
		Instant now = clock.instant();
		settle(order, payment, reservation, tickets, seats, now);

		RefundRecord refund = new RefundRecord(
				UUID.randomUUID(),
				orderId,
				payment.id(),
				payment.amount(),
				order.currency(),
				request.normalizedReason(),
				adminId,
				now);
		require(refundMapper.insert(refund) == 1);

		List<UUID> eventSeatIds = seats.stream().map(EventSeatRecord::id).toList();
		outboxService.recordOrderRefunded(order, refund, eventSeatIds);
		eventPublisher.publishEvent(new SeatsRestocked(reservation.eventId(), eventSeatIds));
		businessMetrics.orderRefunded();
		return RefundResponse.from(refund);
	}

	private void settle(
			OrderRecord order,
			PaymentRecord payment,
			ReservationRecord reservation,
			List<TicketRecord> tickets,
			List<EventSeatRecord> seats,
			Instant now) {
		require(orderMapper.transition(order.id(), OrderStatus.PAID, OrderStatus.REFUNDED, now) == 1);
		require(paymentMapper.updateStatus(
				payment.id(), PaymentStatus.SUCCEEDED, PaymentStatus.REFUNDED, null, now) == 1);
		require(ticketMapper.cancelActiveByOrderId(order.id()) == tickets.size());
		for (EventSeatRecord seat : seats) {
			require(eventSeatMapper.markAvailable(seat.id()) == 1);
		}
		require(reservationMapper.updateStatus(
				reservation.id(), ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, now) == 1);
	}

	/** Locks the seats in a fixed order so two refunds can never deadlock on each other. */
	private List<EventSeatRecord> lockSoldSeats(ReservationRecord reservation) {
		List<UUID> eventSeatIds = reservationItemMapper.findByReservationId(reservation.id()).stream()
				.map(ReservationItemRecord::eventSeatId)
				.distinct()
				.sorted(Comparator.comparing(UUID::toString))
				.toList();
		if (eventSeatIds.isEmpty()) {
			throw new OrderNotRefundableException("The order has no seats to restock");
		}
		List<EventSeatRecord> seats = eventSeatMapper.lockByIds(eventSeatIds);
		boolean consistent = seats.size() == eventSeatIds.size()
				&& seats.stream().allMatch(seat -> seat.permanentStatus() == EventSeatStatus.SOLD
						&& seat.eventId().equals(reservation.eventId()));
		if (!consistent) {
			throw new OrderNotRefundableException("The seats of this order are not in a refundable state");
		}
		return seats;
	}

	private RefundRecord existingRefund(UUID orderId) {
		RefundRecord refund = refundMapper.findByOrderId(orderId);
		if (refund == null) {
			throw new OrderNotRefundableException("The order is refunded but has no refund record");
		}
		return refund;
	}

	/** A step that does not take effect means the order moved under us: abort and roll back. */
	private static void require(boolean applied) {
		if (!applied) {
			throw new OrderNotRefundableException("The order changed while it was being refunded");
		}
	}
}
