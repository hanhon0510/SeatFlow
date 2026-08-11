package com.seatflow.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.seatflow.hold.SeatHoldStore;

@Component
public class SeatHoldReleaseListener {

	private static final Logger log = LoggerFactory.getLogger(SeatHoldReleaseListener.class);

	private final SeatHoldStore seatHoldStore;

	public SeatHoldReleaseListener(SeatHoldStore seatHoldStore) {
		this.seatHoldStore = seatHoldStore;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void release(SeatHoldReleaseRequested event) {
		try {
			seatHoldStore.releaseHold(event.hold());
		}
		catch (RuntimeException ex) {
			log.warn("Failed to release Redis hold {} after committed purchase", event.hold().holdId());
		}
	}
}
