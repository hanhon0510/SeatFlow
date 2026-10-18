package com.seatflow.reminder;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.auth.AuthenticationFailedException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/events/{eventId}/reminder")
public class AdminEventReminderController {

	private final EventReminderService reminderService;

	public AdminEventReminderController(EventReminderService reminderService) {
		this.reminderService = reminderService;
	}

	@GetMapping
	public EventReminderResponse get(@PathVariable UUID eventId) {
		return reminderService.getSettings(eventId);
	}

	@PutMapping
	public EventReminderResponse update(
			@PathVariable UUID eventId,
			@Valid @RequestBody EventReminderRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return reminderService.updateSettings(eventId, adminId(jwt), request);
	}

	private static UUID adminId(Jwt jwt) {
		if (jwt == null) {
			throw new AuthenticationFailedException();
		}
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (IllegalArgumentException ex) {
			throw new AuthenticationFailedException();
		}
	}
}
