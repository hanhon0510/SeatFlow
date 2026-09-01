package com.seatflow.seating;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSeatingController {

	private final SeatingService seatingService;

	public AdminSeatingController(SeatingService seatingService) {
		this.seatingService = seatingService;
	}

	@PostMapping("/venues/{venueId}/sections")
	@ResponseStatus(HttpStatus.CREATED)
	public SectionResponse createSection(
			@PathVariable UUID venueId,
			@Valid @RequestBody SectionCreateRequest request) {
		return seatingService.createSection(venueId, request);
	}

	@PutMapping("/sections/{sectionId}")
	public SectionResponse updateSection(
			@PathVariable UUID sectionId,
			@Valid @RequestBody SectionUpdateRequest request) {
		return seatingService.updateSection(sectionId, request);
	}

	@DeleteMapping("/sections/{sectionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteSection(@PathVariable UUID sectionId) {
		seatingService.deleteSection(sectionId);
	}

	@PostMapping("/sections/{sectionId}/seats")
	@ResponseStatus(HttpStatus.CREATED)
	public SeatResponse createSeat(
			@PathVariable UUID sectionId,
			@Valid @RequestBody SeatCreateRequest request) {
		return seatingService.createSeat(sectionId, request);
	}

	@PostMapping("/sections/{sectionId}/seats/bulk")
	@ResponseStatus(HttpStatus.CREATED)
	public List<SeatResponse> createSeatsBulk(
			@PathVariable UUID sectionId,
			@Valid @RequestBody BulkSeatCreateRequest request) {
		return seatingService.createSeatsBulk(sectionId, request);
	}

	@PutMapping("/seats/{seatId}")
	public SeatResponse updateSeat(
			@PathVariable UUID seatId,
			@Valid @RequestBody SeatUpdateRequest request) {
		return seatingService.updateSeatAccessibility(seatId, request);
	}

	@GetMapping("/venues/{venueId}/seat-layout")
	public SeatLayoutResponse seatLayout(@PathVariable UUID venueId) {
		return seatingService.getSeatLayout(venueId);
	}
}
