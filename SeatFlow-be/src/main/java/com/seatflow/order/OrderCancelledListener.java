package com.seatflow.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.seatflow.hold.SeatHoldService;

/**
 * Frees the seats a cancelled order was holding. This is best effort by design: the cancellation
 * has already committed, and a hold that cannot be released now simply lapses at its TTL.
 */
@Component
public class OrderCancelledListener {

	private static final Logger log = LoggerFactory.getLogger(OrderCancelledListener.class);

	private final SeatHoldService seatHoldService;

	public OrderCancelledListener(SeatHoldService seatHoldService) {
		this.seatHoldService = seatHoldService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void release(OrderCancelled event) {
		try {
			seatHoldService.releaseHold(event.holdId(), event.userId());
		}
		catch (RuntimeException ex) {
			log.warn("Failed to release hold {} after the order was cancelled", event.holdId(), ex);
		}
	}
}
