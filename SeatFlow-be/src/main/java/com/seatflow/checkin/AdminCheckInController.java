package com.seatflow.checkin;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.auth.AuthenticationFailedException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/events/{eventId}/check-in")
public class AdminCheckInController {

	private final CheckInService checkInService;

	public AdminCheckInController(CheckInService checkInService) {
		this.checkInService = checkInService;
	}

	@GetMapping
	public CheckInSummaryResponse summary(@PathVariable UUID eventId) {
		return checkInService.summary(eventId);
	}

	@PostMapping
	public CheckInResponse checkIn(
			@PathVariable UUID eventId,
			@Valid @RequestBody CheckInRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return checkInService.checkIn(eventId, adminId(jwt), request.code());
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
