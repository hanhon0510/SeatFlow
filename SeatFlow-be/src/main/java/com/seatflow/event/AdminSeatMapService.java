package com.seatflow.event;

import java.time.Clock;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AdminSeatMapService {

	private final EventSeatLayoutService eventSeatLayoutService;
	private final EventSalesMapper eventSalesMapper;
	private final Clock clock;

	public AdminSeatMapService(
			EventSeatLayoutService eventSeatLayoutService,
			EventSalesMapper eventSalesMapper,
			Clock clock) {
		this.eventSeatLayoutService = eventSeatLayoutService;
		this.eventSalesMapper = eventSalesMapper;
		this.clock = clock;
	}

	/**
	 * The same per-seat layout a buyer sees - including the live Redis holds, so an admin can
	 * tell a seat that is mid-checkout from one that is genuinely sold - plus the order behind
	 * every seat that has one. No current user is passed: "held by you" is a buyer's notion.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public AdminSeatMapResponse getSeatMap(UUID eventId, UUID sectionId) {
		EventSeatLayoutResponse layout = eventSeatLayoutService.getSeatLayout(eventId, null, sectionId);

		return new AdminSeatMapResponse(
				eventId,
				layout.sections(),
				eventSalesMapper.findSeatOrders(eventId),
				clock.instant());
	}
}
