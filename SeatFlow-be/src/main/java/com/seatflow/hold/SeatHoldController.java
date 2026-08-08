package com.seatflow.hold;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.auth.AuthenticationFailedException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/events/{eventId}/holds")
public class SeatHoldController {

	private final SeatHoldService seatHoldService;

	public SeatHoldController(SeatHoldService seatHoldService) {
		this.seatHoldService = seatHoldService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SeatHoldResponse createHold(
			@PathVariable UUID eventId,
			@Valid @RequestBody SeatHoldRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return seatHoldService.createHold(eventId, userId(jwt), request);
	}

	private static UUID userId(Jwt jwt) {
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
