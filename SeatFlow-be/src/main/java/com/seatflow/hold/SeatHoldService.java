package com.seatflow.hold;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.seatflow.event.EventSeatHoldCandidate;
import com.seatflow.event.EventSeatMapper;
import com.seatflow.event.EventSeatStatus;
import com.seatflow.event.EventStatus;

@Service
public class SeatHoldService {

	private final EventSeatMapper eventSeatMapper;
	private final SeatHoldStore seatHoldStore;
	private final SeatHoldProperties properties;
	private final Clock clock;

	public SeatHoldService(
			EventSeatMapper eventSeatMapper,
			SeatHoldStore seatHoldStore,
			SeatHoldProperties properties,
			Clock clock) {
		this.eventSeatMapper = eventSeatMapper;
		this.seatHoldStore = seatHoldStore;
		this.properties = properties;
		this.clock = clock;
	}

	public SeatHoldResponse createHold(UUID eventId, UUID userId, SeatHoldRequest request) {
		Instant now = clock.instant();
		EventSeatHoldCandidate candidate = eventSeatMapper.findHoldCandidate(eventId, request.eventSeatId());
		validateHoldCandidate(candidate, now);

		UUID holdId = UUID.randomUUID();
		Duration ttl = properties.ttl();
		Instant expiresAt = now.plus(ttl);
		SeatHoldRecord hold = new SeatHoldRecord(
				holdId,
				candidate.eventId(),
				candidate.eventSeatId(),
				candidate.seatId(),
				userId,
				expiresAt);
		if (!tryAcquireSeat(candidate, holdId, ttl)) {
			throw new SeatHoldConflictException();
		}

		try {
			seatHoldStore.storeHold(hold, ttl);
		}
		catch (RuntimeException ex) {
			releaseSeatQuietly(candidate);
			throw new SeatHoldStorageException(ex);
		}

		return new SeatHoldResponse(holdId, candidate.eventId(), candidate.eventSeatId(), userId, expiresAt);
	}

	private boolean tryAcquireSeat(EventSeatHoldCandidate candidate, UUID holdId, Duration ttl) {
		try {
			return seatHoldStore.tryAcquireSeat(candidate.eventId(), candidate.eventSeatId(), holdId, ttl);
		}
		catch (RuntimeException ex) {
			throw new SeatHoldStorageException(ex);
		}
	}

	private static void validateHoldCandidate(EventSeatHoldCandidate candidate, Instant now) {
		if (candidate == null) {
			throw new SeatHoldConflictException();
		}
		if (candidate.eventStatus() != EventStatus.PUBLISHED) {
			throw new SeatHoldConflictException();
		}
		if (now.isBefore(candidate.salesStartTime()) || now.isAfter(candidate.salesEndTime())) {
			throw new SeatHoldConflictException();
		}
		if (candidate.permanentStatus() != EventSeatStatus.AVAILABLE) {
			throw new SeatHoldConflictException();
		}
	}

	private void releaseSeatQuietly(EventSeatHoldCandidate candidate) {
		try {
			seatHoldStore.releaseSeat(candidate.eventId(), candidate.eventSeatId());
		}
		catch (RuntimeException ignored) {
		}
	}
}
