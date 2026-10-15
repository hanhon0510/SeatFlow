package com.seatflow.refund;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.seatflow.seatupdates.SeatStateNotifier;

/** Tells live seat maps that refunded seats are back on sale, but only after the refund is durable. */
@Component
public class SeatsRestockedListener {

	private static final Logger log = LoggerFactory.getLogger(SeatsRestockedListener.class);

	private final SeatStateNotifier seatStateNotifier;

	public SeatsRestockedListener(SeatStateNotifier seatStateNotifier) {
		this.seatStateNotifier = seatStateNotifier;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void restocked(SeatsRestocked event) {
		try {
			seatStateNotifier.seatsReleased(event.eventId(), event.eventSeatIds());
		}
		catch (RuntimeException ex) {
			log.warn("Failed to broadcast restocked seats for event {}", event.eventId(), ex);
		}
	}
}
