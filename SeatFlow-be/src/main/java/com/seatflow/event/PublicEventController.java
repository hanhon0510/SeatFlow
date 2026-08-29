package com.seatflow.event;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.ratelimit.RateLimitService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/events")
public class PublicEventController {

	private final PublicEventCatalogService eventCatalogService;
	private final EventSeatLayoutService eventSeatLayoutService;
	private final RateLimitService rateLimitService;

	public PublicEventController(
			PublicEventCatalogService eventCatalogService,
			EventSeatLayoutService eventSeatLayoutService,
			RateLimitService rateLimitService) {
		this.eventCatalogService = eventCatalogService;
		this.eventSeatLayoutService = eventSeatLayoutService;
		this.rateLimitService = rateLimitService;
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
	public EventSeatLayoutResponse seats(
			@PathVariable UUID eventId,
			@RequestParam(required = false) UUID sectionId,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest servletRequest) {
		rateLimitService.checkSeatLayout(servletRequest);
		return eventSeatLayoutService.getSeatLayout(eventId, userIdOrNull(jwt), sectionId);
	}

	private static UUID userIdOrNull(Jwt jwt) {
		if (jwt == null) {
			return null;
		}
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
