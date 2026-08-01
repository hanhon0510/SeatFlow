package com.seatflow.event;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/events")
public class AdminEventController {

	private final EventService eventService;
	private final EventSectionPricingService eventSectionPricingService;

	public AdminEventController(EventService eventService, EventSectionPricingService eventSectionPricingService) {
		this.eventService = eventService;
		this.eventSectionPricingService = eventSectionPricingService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public EventResponse create(@Valid @RequestBody EventCreateRequest request) {
		return eventService.createEvent(request);
	}

	@GetMapping
	public EventPageResponse list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + EventService.DEFAULT_PAGE_SIZE) int size) {
		return eventService.listEvents(page, size);
	}

	@GetMapping("/{eventId}")
	public EventResponse get(@PathVariable UUID eventId) {
		return eventService.getEvent(eventId);
	}

	@PutMapping("/{eventId}")
	public EventResponse update(@PathVariable UUID eventId, @Valid @RequestBody EventUpdateRequest request) {
		return eventService.updateEvent(eventId, request);
	}

	@PutMapping("/{eventId}/sections")
	public EventSectionConfigurationResponse replaceSections(
			@PathVariable UUID eventId,
			@Valid @RequestBody EventSectionReplaceRequest request) {
		return eventSectionPricingService.replaceEventSections(eventId, request);
	}

	@GetMapping("/{eventId}/sections")
	public EventSectionConfigurationResponse getSections(@PathVariable UUID eventId) {
		return eventSectionPricingService.getEventSections(eventId);
	}
}
