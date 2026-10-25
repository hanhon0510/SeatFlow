package com.seatflow.waitlist;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.seatflow.hold.SeatHoldConflictException;

/**
 * Keeps seats offered from the waitlist for the person they were offered to.
 *
 * <p>Deliberately small and free of the hold and event services, so both can depend on it
 * without a cycle.
 */
@Component
public class WaitlistOfferGuard {

	private static final Logger log = LoggerFactory.getLogger(WaitlistOfferGuard.class);

	private final WaitlistMapper waitlistMapper;
	private final Clock clock;

	public WaitlistOfferGuard(WaitlistMapper waitlistMapper, Clock clock) {
		this.waitlistMapper = waitlistMapper;
		this.clock = clock;
	}

	/** @throws SeatHoldConflictException when any seat is set aside for somebody else */
	public void requireNotOfferedToOthers(UUID eventId, UUID userId, List<UUID> eventSeatIds) {
		Map<UUID, UUID> owners = openOfferOwners(eventId);
		for (UUID eventSeatId : eventSeatIds) {
			UUID owner = owners.get(eventSeatId);
			if (owner != null && !owner.equals(userId)) {
				throw new SeatHoldConflictException();
			}
		}
	}

	/** Records that the buyer took up their offer. Never fails the hold that has already been made. */
	public void holdCreated(UUID eventId, UUID userId, List<UUID> eventSeatIds) {
		try {
			waitlistMapper.fulfillOffer(eventId, userId, eventSeatIds, clock.instant());
		}
		catch (RuntimeException ex) {
			log.warn("Could not close the waitlist offer of user {} for event {}", userId, eventId, ex);
		}
	}

	/** Seat id to the user it is set aside for, for offers that are still open. */
	public Map<UUID, UUID> openOfferOwners(UUID eventId) {
		Instant now = clock.instant();
		Map<UUID, UUID> owners = new HashMap<>();
		for (WaitlistOfferedSeat seat : waitlistMapper.findOpenOfferedSeats(eventId, now)) {
			owners.put(seat.eventSeatId(), seat.userId());
		}
		return owners;
	}
}
