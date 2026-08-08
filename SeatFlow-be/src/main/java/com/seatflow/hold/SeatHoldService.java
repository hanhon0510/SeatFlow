package com.seatflow.hold;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
		List<UUID> requestedEventSeatIds = request.requestedEventSeatIds();
		validateRequestSeatIds(requestedEventSeatIds);

		List<EventSeatHoldCandidate> candidates = orderedCandidates(
				eventId,
				requestedEventSeatIds,
				eventSeatMapper.findHoldCandidates(eventId, requestedEventSeatIds));
		candidates.forEach(candidate -> validateHoldCandidate(candidate, now));

		UUID holdId = UUID.randomUUID();
		Duration ttl = properties.ttl();
		Instant expiresAt = now.plus(ttl);
		EventSeatHoldCandidate firstCandidate = candidates.getFirst();
		SeatHoldRecord hold = new SeatHoldRecord(
				holdId,
				firstCandidate.eventId(),
				candidates.stream().map(EventSeatHoldCandidate::eventSeatId).toList(),
				candidates.stream().map(EventSeatHoldCandidate::seatId).toList(),
				userId,
				expiresAt);
		if (!createHold(hold, ttl)) {
			throw new SeatHoldConflictException();
		}

		return new SeatHoldResponse(
				holdId,
				firstCandidate.eventId(),
				firstCandidate.eventSeatId(),
				hold.eventSeatIds(),
				userId,
				expiresAt);
	}

	private void validateRequestSeatIds(List<UUID> requestedEventSeatIds) {
		if (requestedEventSeatIds.isEmpty()) {
			throw new InvalidSeatHoldRequestException();
		}
		if (requestedEventSeatIds.size() > properties.maxSeats()) {
			throw new InvalidSeatHoldRequestException();
		}
		if (new HashSet<>(requestedEventSeatIds).size() != requestedEventSeatIds.size()) {
			throw new InvalidSeatHoldRequestException();
		}
	}

	private static List<EventSeatHoldCandidate> orderedCandidates(
			UUID eventId,
			List<UUID> requestedEventSeatIds,
			List<EventSeatHoldCandidate> candidates) {
		if (candidates.size() != requestedEventSeatIds.size()) {
			throw new SeatHoldConflictException();
		}

		Map<UUID, EventSeatHoldCandidate> candidatesByEventSeatId = new HashMap<>();
		for (EventSeatHoldCandidate candidate : candidates) {
			if (!eventId.equals(candidate.eventId())) {
				throw new SeatHoldConflictException();
			}
			if (candidatesByEventSeatId.put(candidate.eventSeatId(), candidate) != null) {
				throw new SeatHoldConflictException();
			}
		}

		List<EventSeatHoldCandidate> ordered = new ArrayList<>(requestedEventSeatIds.size());
		for (UUID eventSeatId : requestedEventSeatIds) {
			EventSeatHoldCandidate candidate = candidatesByEventSeatId.get(eventSeatId);
			if (candidate == null) {
				throw new SeatHoldConflictException();
			}
			ordered.add(candidate);
		}
		return ordered;
	}

	private boolean createHold(SeatHoldRecord hold, Duration ttl) {
		try {
			return seatHoldStore.createHold(hold, ttl);
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
}
