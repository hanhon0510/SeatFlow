package com.seatflow.cancellation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.seatflow.event.EventMapper;
import com.seatflow.event.EventNotFoundException;
import com.seatflow.event.EventRecord;
import com.seatflow.event.EventStateConflictException;
import com.seatflow.event.EventStatus;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.order.OrderCancelled;
import com.seatflow.refund.RefundRequest;
import com.seatflow.refund.RefundService;

/**
 * Cancels an event and gives every buyer their money back.
 *
 * <p>Cancelling is split in two on purpose. The status change is one short transaction: the event
 * closes for sale, unpaid checkouts are voided and nothing new can be paid for. Refunds then run as
 * one transaction per order, so one order that cannot be refunded never holds back the others, and
 * the work can be repeated until nothing is left. Each call refunds a bounded batch to keep the
 * request short; the response says how much remains.
 */
@Service
public class EventCancellationService {

	static final int REFUND_BATCH_SIZE = 50;

	private static final Logger log = LoggerFactory.getLogger(EventCancellationService.class);

	private final EventMapper eventMapper;
	private final EventCancellationMapper cancellationMapper;
	private final RefundService refundService;
	private final ApplicationEventPublisher eventPublisher;
	private final BusinessMetrics businessMetrics;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public EventCancellationService(
			EventMapper eventMapper,
			EventCancellationMapper cancellationMapper,
			RefundService refundService,
			ApplicationEventPublisher eventPublisher,
			BusinessMetrics businessMetrics,
			TransactionTemplate transactionTemplate,
			Clock clock) {
		this.eventMapper = eventMapper;
		this.cancellationMapper = cancellationMapper;
		this.refundService = refundService;
		this.eventPublisher = eventPublisher;
		this.businessMetrics = businessMetrics;
		this.transactionTemplate = transactionTemplate;
		this.clock = clock;
	}

	public EventCancellationResponse cancelEvent(UUID eventId, UUID adminId, EventCancellationRequest request) {
		EventCancellationRecord cancellation = transactionTemplate.execute(
				status -> closeEvent(eventId, adminId, request));
		refundBatch(cancellation, adminId);
		return EventCancellationResponse.of(cancellation, cancellationMapper.refundCounts(eventId));
	}

	private EventCancellationRecord closeEvent(UUID eventId, UUID adminId, EventCancellationRequest request) {
		// The exclusive lock waits for payments in flight (they hold it shared) and serialises two
		// admins cancelling at once; the loser finds the event already cancelled and just carries on.
		EventRecord event = eventMapper.findByIdForUpdate(eventId);
		if (event == null) {
			throw new EventNotFoundException();
		}
		Instant now = clock.instant();
		if (event.status() == EventStatus.CANCELLED) {
			return recordedCancellation(eventId, adminId, request, now);
		}
		if (event.status() == EventStatus.COMPLETED || !event.startTime().isAfter(now)) {
			throw new EventStateConflictException();
		}
		if (eventMapper.cancel(eventId) != 1) {
			throw new EventStateConflictException();
		}
		EventCancellationRecord cancellation = recordedCancellation(eventId, adminId, request, now);

		// Nobody can pay for a seat of this event any more, so unpaid checkouts are closed and their
		// holds freed once this transaction commits.
		List<OpenHold> openHolds = cancellationMapper.findOpenHolds(eventId);
		cancellationMapper.cancelPendingOrders(eventId, now);
		cancellationMapper.cancelOpenReservations(eventId, now);
		for (OpenHold hold : openHolds) {
			eventPublisher.publishEvent(new OrderCancelled(hold.holdId(), hold.userId()));
		}
		businessMetrics.eventCancelled();
		return cancellation;
	}

	private EventCancellationRecord recordedCancellation(
			UUID eventId, UUID adminId, EventCancellationRequest request, Instant now) {
		cancellationMapper.insert(new EventCancellationRecord(eventId, request.normalizedReason(), adminId, now));
		EventCancellationRecord stored = cancellationMapper.findByEventId(eventId);
		if (stored == null) {
			throw new EventStateConflictException();
		}
		return stored;
	}

	private void refundBatch(EventCancellationRecord cancellation, UUID adminId) {
		RefundRequest refundRequest = new RefundRequest(refundReason(cancellation));
		for (UUID orderId : cancellationMapper.findRefundableOrderIds(cancellation.eventId(), REFUND_BATCH_SIZE)) {
			try {
				refundService.refundOrder(orderId, adminId, refundRequest);
			}
			catch (RuntimeException ex) {
				// Left PAID, so it still counts as remaining and the next call tries it again.
				log.warn("Could not refund order {} of cancelled event {}", orderId, cancellation.eventId(), ex);
			}
		}
	}

	private static String refundReason(EventCancellationRecord cancellation) {
		return cancellation.reason() == null ? "Event cancelled" : "Event cancelled: " + cancellation.reason();
	}
}
