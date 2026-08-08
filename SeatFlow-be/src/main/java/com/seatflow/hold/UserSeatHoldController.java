package com.seatflow.hold;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.auth.AuthenticationFailedException;

@RestController
@RequestMapping("/api/v1/holds")
public class UserSeatHoldController {

	private final SeatHoldService seatHoldService;

	public UserSeatHoldController(SeatHoldService seatHoldService) {
		this.seatHoldService = seatHoldService;
	}

	@GetMapping("/{holdId}")
	public SeatHoldResponse getHold(
			@PathVariable UUID holdId,
			@AuthenticationPrincipal Jwt jwt) {
		return seatHoldService.getHold(holdId, userId(jwt));
	}

	@DeleteMapping("/{holdId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void releaseHold(
			@PathVariable UUID holdId,
			@AuthenticationPrincipal Jwt jwt) {
		seatHoldService.releaseHold(holdId, userId(jwt));
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
