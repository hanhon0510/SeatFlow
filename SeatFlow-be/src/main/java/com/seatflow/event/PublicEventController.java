package com.seatflow.event;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class PublicEventController {

	private final PublicEventCatalogService eventCatalogService;
	private final EventSeatLayoutService eventSeatLayoutService;

	public PublicEventController(
			PublicEventCatalogService eventCatalogService,
			EventSeatLayoutService eventSeatLayoutService) {
		this.eventCatalogService = eventCatalogService;
		this.eventSeatLayoutService = eventSeatLayoutService;
	}

	@GetMapping
	public PublicEventPageResponse list(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) UUID venueId,
			@RequestParam(required = false) String startDate,
			@RequestParam(required = false) String endDate,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + PublicEventCatalogService.DEFAULT_PAGE_SIZE) int size,
			@RequestParam(required = false) String sort) {
		return eventCatalogService.listEvents(search, venueId, startDate, endDate, page, size, sort);
	}

	@GetMapping("/{eventId}")
	public PublicEventResponse get(@PathVariable UUID eventId) {
		return eventCatalogService.getEvent(eventId);
	}

	@GetMapping("/{eventId}/seats")
	public EventSeatLayoutResponse seats(@PathVariable UUID eventId) {
		return eventSeatLayoutService.getSeatLayout(eventId);
	}
}
