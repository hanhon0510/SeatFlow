package com.seatflow.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
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
import com.seatflow.support.JwtTestSupport;
import com.seatflow.user.CurrentUserService;
import com.seatflow.user.UserController;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

@WebMvcTest({ AuthController.class, UserController.class })
@Import({
		SecurityConfig.class,
		JwtConfig.class,
		JwtTokenService.class,
		LoginService.class,
		CurrentUserService.class
})
@TestPropertySource(properties = {
		"seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET,
		"seatflow.jwt.issuer=" + JwtTestSupport.ISSUER
})
class AuthWebSecurityTests {

	private static final Instant NOW = Instant.parse("2099-01-01T00:00:00Z");
	private static final String PASSWORD = "StrongPassword123!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	private RegistrationService registrationService;

	@MockitoBean
	private UserMapper userMapper;

	@Test
	void loginTokenAccessesCurrentUser() throws Exception {
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
