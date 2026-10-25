package com.seatflow.waitlist;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/events/{eventId}/waitlist")
public class AdminWaitlistController {

	private final WaitlistService waitlistService;

	public AdminWaitlistController(WaitlistService waitlistService) {
		this.waitlistService = waitlistService;
	}

	@GetMapping
	public WaitlistSummaryResponse summary(@PathVariable UUID eventId) {
		return waitlistService.getSummary(eventId);
	}
}
