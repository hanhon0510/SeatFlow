package com.seatflow.cancellation;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.auth.AuthenticationFailedException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/events")
public class AdminEventCancellationController {

	private final EventCancellationService eventCancellationService;

	public AdminEventCancellationController(EventCancellationService eventCancellationService) {
		this.eventCancellationService = eventCancellationService;
	}

	/** Cancels the event on the first call and keeps refunding on every call after it. */
	@PostMapping("/{eventId}/cancel")
	public EventCancellationResponse cancel(
			@PathVariable UUID eventId,
			@Valid @RequestBody(required = false) EventCancellationRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return eventCancellationService.cancelEvent(
				eventId, adminId(jwt), request == null ? EventCancellationRequest.NONE : request);
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
