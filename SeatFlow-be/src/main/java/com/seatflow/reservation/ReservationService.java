package com.seatflow.reservation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seatflow.event.EventSeatStatus;
import com.seatflow.hold.SeatHoldNotFoundException;
import com.seatflow.hold.SeatHoldResponse;
import com.seatflow.hold.SeatHoldService;

@Service
public class ReservationService {

	private final ReservationMapper reservationMapper;
	private final ReservationItemMapper reservationItemMapper;
	private final SeatHoldService seatHoldService;
	private final Clock clock;

	public ReservationService(
			ReservationMapper reservationMapper,
			ReservationItemMapper reservationItemMapper,
			SeatHoldService seatHoldService,
			Clock clock) {
		this.reservationMapper = reservationMapper;
		this.reservationItemMapper = reservationItemMapper;
		this.seatHoldService = seatHoldService;
		this.clock = clock;
	}

	@Transactional
	public ReservationResponse createReservation(UUID userId, ReservationCreateRequest request) {
		ReservationRecord existing = reservationMapper.findByHoldId(request.holdId());
		if (existing != null) {
			return responseForOwner(existing, userId);
		}

		SeatHoldResponse hold = seatHoldService.getHold(request.holdId(), userId);
		validateOwner(hold.userId(), userId);
		Instant now = clock.instant();
		if (!hold.expiresAt().isAfter(now)) {
			throw new SeatHoldNotFoundException();
		}

		List<ReservationSeatPrice> seatPrices = orderedSeatPrices(hold);
		BigDecimal totalAmount = seatPrices.stream()
				.map(ReservationSeatPrice::price)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		ReservationRecord reservation = ReservationRecord.pending(
				UUID.randomUUID(),
				userId,
				hold.eventId(),
				hold.holdId(),
				hold.expiresAt(),
				totalAmount,
				now);

		if (reservationMapper.insert(reservation) == 0) {
			ReservationRecord concurrentReservation = reservationMapper.findByHoldId(hold.holdId());
			if (concurrentReservation == null) {
				throw new ReservationConflictException();
			}
			return responseForOwner(concurrentReservation, userId);
		}

		List<ReservationItemRecord> items = seatPrices.stream()
				.map(seatPrice -> new ReservationItemRecord(
						UUID.randomUUID(),
						reservation.id(),
						seatPrice.eventSeatId(),
						seatPrice.price(),
						now))
				.toList();
		if (reservationItemMapper.batchInsert(items) != items.size()) {
			throw new ReservationConflictException();
		}

		return ReservationResponse.from(reservation, items);
	}

	@Transactional(readOnly = true)
	public ReservationResponse getReservation(UUID reservationId, UUID userId) {
		ReservationRecord reservation = reservationMapper.findByIdAndUser(reservationId, userId);
		if (reservation == null) {
			throw new ReservationNotFoundException();
		}
		return ReservationResponse.from(
				reservation,
				reservationItemMapper.findByReservationId(reservation.id()));
	}

	private List<ReservationSeatPrice> orderedSeatPrices(SeatHoldResponse hold) {
		List<ReservationSeatPrice> storedPrices = reservationMapper.findSeatPricesForEvent(
				hold.eventId(),
				hold.eventSeatIds());
		if (storedPrices.size() != hold.eventSeatIds().size()) {
			throw new ReservationConflictException();
		}

		Map<UUID, ReservationSeatPrice> pricesByEventSeatId = new HashMap<>();
		for (ReservationSeatPrice seatPrice : storedPrices) {
			if (!hold.eventId().equals(seatPrice.eventId())
					|| seatPrice.price() == null
					|| seatPrice.price().signum() < 0
					|| seatPrice.permanentStatus() != EventSeatStatus.AVAILABLE
					|| pricesByEventSeatId.put(seatPrice.eventSeatId(), seatPrice) != null) {
				throw new ReservationConflictException();
			}
		}

		List<ReservationSeatPrice> orderedPrices = new ArrayList<>(hold.eventSeatIds().size());
		for (UUID eventSeatId : hold.eventSeatIds()) {
			ReservationSeatPrice seatPrice = pricesByEventSeatId.get(eventSeatId);
			if (seatPrice == null) {
				throw new ReservationConflictException();
			}
			orderedPrices.add(seatPrice);
		}
		return orderedPrices;
	}

	private ReservationResponse responseForOwner(ReservationRecord reservation, UUID userId) {
		validateOwner(reservation.userId(), userId);
		return ReservationResponse.from(
				reservation,
				reservationItemMapper.findByReservationId(reservation.id()));
	}

	private static void validateOwner(UUID ownerId, UUID userId) {
		if (!ownerId.equals(userId)) {
			throw new AccessDeniedException("Reservation belongs to another user");
		}
	}
}
