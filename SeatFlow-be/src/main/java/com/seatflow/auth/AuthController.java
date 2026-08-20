package com.seatflow.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.seatflow.ratelimit.RateLimitService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final RegistrationService registrationService;
	private final LoginService loginService;
	private final RefreshTokenService refreshTokenService;
	private final RefreshTokenCookieService refreshTokenCookieService;
	private final RateLimitService rateLimitService;

	public AuthController(
			RegistrationService registrationService,
			LoginService loginService,
			RefreshTokenService refreshTokenService,
			RefreshTokenCookieService refreshTokenCookieService,
			RateLimitService rateLimitService) {
		this.registrationService = registrationService;
		this.loginService = loginService;
		this.refreshTokenService = refreshTokenService;
		this.refreshTokenCookieService = refreshTokenCookieService;
		this.rateLimitService = rateLimitService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public RegisterResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
		rateLimitService.checkRegister(servletRequest, request.email());
		return registrationService.register(request);
	}

	@PostMapping("/login")
	public LoginResponse login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest servletRequest,
			HttpServletResponse response) {
		rateLimitService.checkLogin(servletRequest, request.email());
		AuthSession session = loginService.login(request);
		refreshTokenCookieService.writeToken(response, session.refreshToken());
		return session.accessToken();
	}

	@PostMapping("/refresh")
	public LoginResponse refresh(HttpServletRequest request, HttpServletResponse response) {
		AuthSession session = refreshTokenService.refresh(refreshTokenCookieService.requireToken(request));
		refreshTokenCookieService.writeToken(response, session.refreshToken());
		return session.accessToken();
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		refreshTokenService.logout(refreshTokenCookieService.findToken(request).orElse(null));
		refreshTokenCookieService.clearToken(response);
	}

}
