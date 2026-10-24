package com.seatflow.account;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.auth.AuthenticationFailedException;
import com.seatflow.auth.RefreshTokenCookieService;
import com.seatflow.ratelimit.RateLimitService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users/me/password")
public class UserPasswordController {

	private final PasswordChangeService passwordChangeService;
	private final RefreshTokenCookieService refreshTokenCookieService;
	private final RateLimitService rateLimitService;

	public UserPasswordController(
			PasswordChangeService passwordChangeService,
			RefreshTokenCookieService refreshTokenCookieService,
			RateLimitService rateLimitService) {
		this.passwordChangeService = passwordChangeService;
		this.refreshTokenCookieService = refreshTokenCookieService;
		this.rateLimitService = rateLimitService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(
			@Valid @RequestBody ChangePasswordRequest request,
			HttpServletRequest servletRequest,
			@AuthenticationPrincipal Jwt jwt) {
		UUID userId = userId(jwt);
		rateLimitService.checkPasswordChange(userId);
		passwordChangeService.changePassword(
				userId, request, refreshTokenCookieService.findToken(servletRequest).orElse(null));
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
