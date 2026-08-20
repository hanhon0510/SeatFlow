package com.seatflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.security.JwtConfig;
import com.seatflow.security.JwtProperties;
import com.seatflow.security.JwtTokenService;
import com.seatflow.security.SecurityConfig;
import com.seatflow.ratelimit.RateLimitExceededException;
import com.seatflow.ratelimit.RateLimitResult;
import com.seatflow.ratelimit.RateLimitService;
import com.seatflow.support.JwtTestSupport;
import com.seatflow.user.CurrentUserService;
import com.seatflow.user.UserController;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

import jakarta.servlet.http.Cookie;

@WebMvcTest({ AuthController.class, UserController.class })
@Import({
		SecurityConfig.class,
		JwtConfig.class,
		JwtTokenService.class,
		LoginService.class,
		RefreshTokenConfig.class,
		RefreshTokenCookieService.class,
		RefreshTokenService.class,
		SecureRefreshTokenGenerator.class,
		CurrentUserService.class
})
@TestPropertySource(properties = {
		"server.port=8080",
		"seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET,
		"seatflow.jwt.issuer=" + JwtTestSupport.ISSUER,
		"seatflow.jwt.expires-in-seconds=900",
		"seatflow.refresh-token.cookie-name=seatflow_refresh_token",
		"seatflow.refresh-token.expires-in-seconds=1209600",
		"seatflow.refresh-token.cookie-secure=false",
		"seatflow.refresh-token.same-site=Strict"
})
class AuthWebSecurityTests {

	private static final Instant NOW = Instant.parse("2099-01-01T00:00:00Z");
	private static final String PASSWORD = "StrongPassword123!";
	private static final String REFRESH_COOKIE_NAME = "seatflow_refresh_token";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RefreshTokenGenerator refreshTokenGenerator;

	@MockitoBean
	private RegistrationService registrationService;

	@MockitoBean
	private RateLimitService rateLimitService;

	@MockitoBean
	private UserMapper userMapper;

	@MockitoBean
	private RefreshTokenMapper refreshTokenMapper;

	@Test
	void loginSetsRefreshCookieAndAccessTokenAccessesCurrentUser() throws Exception {
		UserRecord user = user();
		when(userMapper.findByNormalizedEmail(user.email())).thenReturn(user);
		when(userMapper.findById(user.id())).thenReturn(user);

		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("User@Example.COM", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(REFRESH_COOKIE_NAME + "=")))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
				.andExpect(content().string(not(containsString(PASSWORD))))
				.andReturn();

		String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
				.get("accessToken")
				.asText();

		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.id().toString()))
				.andExpect(jsonPath("$.email").value(user.email()))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void rateLimitedLoginReturnsRetryInformation() throws Exception {
		org.mockito.Mockito.doThrow(new RateLimitExceededException(new RateLimitResult(
				false,
				2,
				0,
				Duration.ofSeconds(30),
				Duration.ofSeconds(30))))
				.when(rateLimitService)
				.checkLogin(any(jakarta.servlet.http.HttpServletRequest.class), eq("User@Example.COM"));

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("User@Example.COM", PASSWORD)))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "30"))
				.andExpect(header().string("X-RateLimit-Limit", "2"))
				.andExpect(header().string("X-RateLimit-Remaining", "0"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Rate limit exceeded"))
				.andExpect(jsonPath("$.data.limit").value(2))
				.andExpect(jsonPath("$.data.remaining").value(0))
				.andExpect(jsonPath("$.data.retryAfterSeconds").value(30));
	}

	@Test
	void refreshRotatesCookieAndReturnsNewAccessToken() throws Exception {
		UserRecord user = user();
		String oldToken = "old-refresh-token";
		String oldTokenHash = refreshTokenGenerator.hashToken(oldToken);
		RefreshTokenRecord existing = refreshTokenRecord(user.id(), oldTokenHash, null, NOW.plusSeconds(1_209_600));
		when(refreshTokenMapper.findActiveByHash(eq(oldTokenHash), any(Instant.class))).thenReturn(existing);
		when(refreshTokenMapper.revoke(eq(existing.id()), any(Instant.class))).thenReturn(1);
		when(userMapper.findById(user.id())).thenReturn(user);

		MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
						.cookie(new Cookie(REFRESH_COOKIE_NAME, oldToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(REFRESH_COOKIE_NAME + "=")))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString(oldToken))))
				.andReturn();

		String token = objectMapper.readTree(result.getResponse().getContentAsString())
				.get("accessToken")
				.asText();
		assertThat(token).isNotBlank();

		ArgumentCaptor<RefreshTokenRecord> insertedToken = ArgumentCaptor.forClass(RefreshTokenRecord.class);
		verify(refreshTokenMapper).revoke(eq(existing.id()), any(Instant.class));
		verify(refreshTokenMapper).insert(insertedToken.capture());
		assertThat(insertedToken.getValue().tokenHash()).isNotEqualTo(oldTokenHash);
	}

	@Test
	void revokedRefreshTokenIsRejectedAsReuseAttempt() throws Exception {
		UserRecord user = user();
		String rawToken = "revoked-refresh-token";
		String tokenHash = refreshTokenGenerator.hashToken(rawToken);
		when(refreshTokenMapper.findActiveByHash(eq(tokenHash), any(Instant.class))).thenReturn(null);
		when(refreshTokenMapper.findByHash(tokenHash))
				.thenReturn(refreshTokenRecord(user.id(), tokenHash, NOW.minusSeconds(1), NOW.plusSeconds(1_209_600)));

		mockMvc.perform(post("/api/v1/auth/refresh")
						.cookie(new Cookie(REFRESH_COOKIE_NAME, rawToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid refresh token"));

		verify(refreshTokenMapper).revokeAllUserTokens(eq(user.id()), any(Instant.class));
	}

	@Test
	void expiredRefreshTokenIsRejected() throws Exception {
		UserRecord user = user();
		String rawToken = "expired-refresh-token";
		String tokenHash = refreshTokenGenerator.hashToken(rawToken);
		when(refreshTokenMapper.findActiveByHash(eq(tokenHash), any(Instant.class))).thenReturn(null);
		when(refreshTokenMapper.findByHash(tokenHash))
				.thenReturn(refreshTokenRecord(user.id(), tokenHash, null, NOW.minusSeconds(1)));

		mockMvc.perform(post("/api/v1/auth/refresh")
						.cookie(new Cookie(REFRESH_COOKIE_NAME, rawToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid refresh token"));
	}

	@Test
	void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
		UserRecord user = user();
		String rawToken = "logout-refresh-token";
		String tokenHash = refreshTokenGenerator.hashToken(rawToken);
		RefreshTokenRecord existing = refreshTokenRecord(user.id(), tokenHash, null, NOW.plusSeconds(1_209_600));
		when(refreshTokenMapper.findByHash(tokenHash)).thenReturn(existing);

		mockMvc.perform(post("/api/v1/auth/logout")
						.cookie(new Cookie(REFRESH_COOKIE_NAME, rawToken)))
				.andExpect(status().isNoContent())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(REFRESH_COOKIE_NAME + "=")))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));

		verify(refreshTokenMapper).revoke(eq(existing.id()), any(Instant.class));
	}

	@Test
	void expiredTokenCannotAccessCurrentUser() throws Exception {
		String token = tokenWith(JwtTestSupport.DEFAULT_SECRET, 1, Instant.parse("2024-01-01T00:00:00Z"));

		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Unauthorized"));
	}

	@Test
	void invalidSignatureCannotAccessCurrentUser() throws Exception {
		String token = tokenWith(JwtTestSupport.OTHER_SECRET, 900, NOW);

		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Unauthorized"));
	}

	private UserRecord user() {
		return new UserRecord(
				UUID.randomUUID(),
				"user@example.com",
				passwordEncoder.encode(PASSWORD),
				UserRole.USER,
				UserStatus.ACTIVE,
				NOW,
				NOW);
	}

	private static RefreshTokenRecord refreshTokenRecord(
			UUID userId,
			String tokenHash,
			Instant revokedAt,
			Instant expiresAt) {
		return new RefreshTokenRecord(
				UUID.randomUUID(),
				userId,
				tokenHash,
				expiresAt,
				revokedAt,
				NOW);
	}

	private static String tokenWith(String secret, long expiresInSeconds, Instant issuedAt) {
		JwtProperties jwtProperties = new JwtProperties(secret, JwtTestSupport.ISSUER, expiresInSeconds);
		SecretKey secretKey = JwtTestSupport.secretKey(secret);
		JwtTokenService jwtTokenService = new JwtTokenService(
				JwtTestSupport.encoder(secretKey),
				jwtProperties,
				Clock.fixed(issuedAt, ZoneOffset.UTC));
		return jwtTokenService.issueAccessToken(new UserRecord(
				UUID.randomUUID(),
				"user@example.com",
				"password-hash",
				UserRole.USER,
				UserStatus.ACTIVE,
				issuedAt,
				issuedAt))
				.accessToken();
	}

	private static String loginJson(String email, String password) {
		return """
				{
				  "email": "%s",
				  "password": "%s"
				}
				""".formatted(email, password);
	}

}
