package com.seatflow.venue;

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
@RequestMapping("/api/v1/admin/venues")
public class AdminVenueController {

	private final VenueService venueService;

	public AdminVenueController(VenueService venueService) {
		this.venueService = venueService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public VenueResponse create(@Valid @RequestBody VenueCreateRequest request) {
		return venueService.createVenue(request);
	}

	@GetMapping
	public VenuePageResponse list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + VenueService.DEFAULT_PAGE_SIZE) int size) {
		return venueService.listVenues(page, size);
	}

	@GetMapping("/{venueId}")
	public VenueResponse get(@PathVariable UUID venueId) {
		return venueService.getVenue(venueId);
	}

	@PutMapping("/{venueId}")
	public VenueResponse update(@PathVariable UUID venueId, @Valid @RequestBody VenueUpdateRequest request) {
		return venueService.updateVenue(venueId, request);
	}

	@PostMapping("/{venueId}/archive")
	public VenueResponse archive(@PathVariable UUID venueId) {
		return venueService.archiveVenue(venueId);
	}
}
