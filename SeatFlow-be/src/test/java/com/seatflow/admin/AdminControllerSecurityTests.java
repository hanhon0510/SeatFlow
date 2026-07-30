package com.seatflow.admin;

import static org.mockito.Mockito.when;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.seatflow.security.JwtConfig;
import com.seatflow.security.JwtTokenService;
import com.seatflow.security.SecurityConfig;
import com.seatflow.support.JwtTestSupport;
import com.seatflow.user.CurrentUserService;
import com.seatflow.user.UserMapper;
import com.seatflow.user.UserRecord;
import com.seatflow.user.UserRole;
import com.seatflow.user.UserStatus;

@WebMvcTest(AdminController.class)
@Import({
		SecurityConfig.class,
		JwtConfig.class,
		JwtTokenService.class,
		AdminService.class,
		CurrentUserService.class
})
@TestPropertySource(properties = {
		"server.port=8080",
		"seatflow.jwt.secret=" + JwtTestSupport.DEFAULT_SECRET,
		"seatflow.jwt.issuer=" + JwtTestSupport.ISSUER,
		"seatflow.jwt.expires-in-seconds=900"
})
class AdminControllerSecurityTests {

	private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private UserMapper userMapper;

	@Test
	void adminCanAccessAdminEndpoint() throws Exception {
		UserRecord admin = user(UserRole.ADMIN);
		when(userMapper.findById(admin.id())).thenReturn(admin);

		mockMvc.perform(get("/api/v1/admin")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(admin.id().toString()))
				.andExpect(jsonPath("$.email").value(admin.email()))
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void normalUserReceivesForbiddenAtAdminEndpoint() throws Exception {
		UserRecord user = user(UserRole.USER);

		mockMvc.perform(get("/api/v1/admin")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Forbidden"));
	}

	@Test
	void unauthenticatedRequestReceivesUnauthorizedAtAdminEndpoint() throws Exception {
		mockMvc.perform(get("/api/v1/admin"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Unauthorized"));
	}

	@Test
	void adminClaimIsRejectedWhenPersistedUserIsNotAdmin() throws Exception {
		UserRecord tokenUser = user(UserRole.ADMIN);
		UserRecord persistedUser = new UserRecord(
				tokenUser.id(),
				tokenUser.email(),
				tokenUser.passwordHash(),
				UserRole.USER,
				tokenUser.status(),
				tokenUser.createdAt(),
				tokenUser.updatedAt());
		when(userMapper.findById(tokenUser.id())).thenReturn(persistedUser);

		mockMvc.perform(get("/api/v1/admin")
						.header(HttpHeaders.AUTHORIZATION, bearerToken(tokenUser)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Forbidden"));
	}

	private String bearerToken(UserRecord user) {
		return "Bearer " + jwtTokenService.issueAccessToken(user).accessToken();
	}

	private static UserRecord user(UserRole role) {
		return new UserRecord(
				UUID.randomUUID(),
				"%s@example.com".formatted(role.name().toLowerCase()),
				"password-hash",
				role,
				UserStatus.ACTIVE,
				NOW,
				NOW);
	}
}
