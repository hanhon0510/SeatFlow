package com.seatflow.auth;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class RefreshTokenCookieService {

	private static final String COOKIE_PATH = "/api/v1/auth";

	private final RefreshTokenProperties refreshTokenProperties;

	public RefreshTokenCookieService(RefreshTokenProperties refreshTokenProperties) {
		this.refreshTokenProperties = refreshTokenProperties;
	}

	public String requireToken(HttpServletRequest request) {
		return findToken(request).orElseThrow(InvalidRefreshTokenException::new);
	}

	public Optional<String> findToken(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}

		return Arrays.stream(cookies)
				.filter(cookie -> refreshTokenProperties.cookieName().equals(cookie.getName()))
				.map(Cookie::getValue)
				.filter(StringUtils::hasText)
				.findFirst();
	}

	public void writeToken(HttpServletResponse response, IssuedRefreshToken refreshToken) {
		ResponseCookie cookie = ResponseCookie.from(refreshTokenProperties.cookieName(), refreshToken.token())
				.httpOnly(true)
				.secure(refreshTokenProperties.cookieSecure())
				.sameSite(refreshTokenProperties.sameSite())
				.path(COOKIE_PATH)
				.maxAge(Duration.ofSeconds(refreshTokenProperties.expiresInSeconds()))
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public void clearToken(HttpServletResponse response) {
		ResponseCookie cookie = ResponseCookie.from(refreshTokenProperties.cookieName(), "")
				.httpOnly(true)
				.secure(refreshTokenProperties.cookieSecure())
				.sameSite(refreshTokenProperties.sameSite())
				.path(COOKIE_PATH)
				.maxAge(Duration.ZERO)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}
}
