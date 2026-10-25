package com.seatflow.waitlist;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.auth.AuthenticationFailedException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/events/{eventId}/waitlist")
public class WaitlistController {

	private final WaitlistService waitlistService;

	public WaitlistController(WaitlistService waitlistService) {
		this.waitlistService = waitlistService;
	}

	@GetMapping
	public WaitlistStatusResponse status(@PathVariable UUID eventId, @AuthenticationPrincipal Jwt jwt) {
		return waitlistService.getStatus(eventId, userId(jwt));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public WaitlistStatusResponse join(
			@PathVariable UUID eventId,
			@Valid @RequestBody WaitlistJoinRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return waitlistService.join(eventId, userId(jwt), request);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leave(@PathVariable UUID eventId, @AuthenticationPrincipal Jwt jwt) {
		waitlistService.leave(eventId, userId(jwt));
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
