package com.seatflow.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.seatflow.security.SecurityConfig;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "server.port=8080")
class UserControllerSecurityTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CurrentUserService currentUserService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void validJwtAccessesCurrentUser() throws Exception {
		UUID userId = UUID.randomUUID();
		Instant now = Instant.parse("2026-07-25T00:00:00Z");
		when(currentUserService.getCurrentUser(any()))
				.thenReturn(new UserMeResponse(
						userId,
						"user@example.com",
						UserRole.USER,
						UserStatus.ACTIVE,
						now,
						now));

		mockMvc.perform(get("/api/v1/users/me")
						.with(jwt().jwt(builder -> builder
								.subject(userId.toString())
								.claim("role", UserRole.USER.name()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId.toString()))
				.andExpect(jsonPath("$.email").value("user@example.com"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void missingJwtIsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.correlationId").isNotEmpty())
				.andExpect(jsonPath("$.title").value("Unauthorized"));
	}

	@Test
	void expiredJwtIsRejected() throws Exception {
		when(jwtDecoder.decode("expired-token")).thenThrow(new BadJwtException("expired"));

		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Unauthorized"));
	}

	@Test
	void invalidJwtIsRejected() throws Exception {
		when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid signature"));

		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Unauthorized"));
	}

}
